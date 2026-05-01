package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WaitAndCheckCommunicationWorkflowStep implements WorkflowStepType {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String FUB_COMM_CHECK_TRANSIENT = "FUB_COMM_CHECK_TRANSIENT";
    static final String FUB_COMM_CHECK_PERMANENT = "FUB_COMM_CHECK_PERMANENT";
    static final String COMM_CHECK_EXECUTION_ERROR = "COMM_CHECK_EXECUTION_ERROR";
    static final String CONVERSATIONAL = "CONVERSATIONAL";
    static final String CONNECTED_NON_CONVERSATIONAL = "CONNECTED_NON_CONVERSATIONAL";
    static final String COMM_NOT_FOUND = "COMM_NOT_FOUND";

    private static final int DEFAULT_LOOKBACK_MINUTES = 30;
    private static final int MIN_LOOKBACK_MINUTES = 1;

    private static final Logger log = LoggerFactory.getLogger(WaitAndCheckCommunicationWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;
    private final ProcessedCallRepository processedCallRepository;
    private final CallOutcomeRulesProperties callOutcomeRulesProperties;
    private final Clock clock;

    public WaitAndCheckCommunicationWorkflowStep(
            FollowUpBossClient followUpBossClient,
            FubCallHelper fubCallHelper,
            ProcessedCallRepository processedCallRepository,
            CallOutcomeRulesProperties callOutcomeRulesProperties,
            Clock clock) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
        this.processedCallRepository = processedCallRepository;
        this.callOutcomeRulesProperties = callOutcomeRulesProperties;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "wait_and_check_communication";
    }

    @Override
    public String displayName() {
        return "Wait & Check Communication";
    }

    @Override
    public String description() {
        return "Wait for a specified duration, then classify local call evidence and fallback to Follow Up Boss when needed.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "delayMinutes", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "description", "Minutes to wait before checking communication status"),
                        "lookbackMinutes", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "description", "Optional local call evidence lookback window in minutes (default 30)")));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of(CONVERSATIONAL, CONNECTED_NON_CONVERSATIONAL, COMM_NOT_FOUND);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.DEFAULT_FUB;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        long personId;
        try {
            personId = fubCallHelper.parsePersonId(context.sourceLeadId());
        } catch (IllegalArgumentException ex) {
            String code = (context.sourceLeadId() == null || context.sourceLeadId().isBlank())
                    ? SOURCE_LEAD_ID_MISSING : SOURCE_LEAD_ID_INVALID;
            return StepExecutionResult.failure(code, ex.getMessage());
        }

        try {
            String normalizedSourceLeadId = String.valueOf(personId);
            String localDecision = resolveFromLocalEvidence(
                    normalizedSourceLeadId, context.resolvedConfig(), context.rawConfig());
            if (localDecision != null) {
                return StepExecutionResult.success(localDecision);
            }

            PersonCommunicationCheckResult checkResult =
                    fubCallHelper.executeWithRetry(() -> followUpBossClient.checkPersonCommunication(personId));
            if (checkResult == null) {
                return StepExecutionResult.success(COMM_NOT_FOUND);
            }
            return checkResult.communicationFound()
                    ? StepExecutionResult.success(CONNECTED_NON_CONVERSATIONAL)
                    : StepExecutionResult.success(COMM_NOT_FOUND);
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(
                    FUB_COMM_CHECK_TRANSIENT,
                    "Transient failure checking communication for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(
                    FUB_COMM_CHECK_PERMANENT,
                    "Permanent failure checking communication for person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected communication check execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return StepExecutionResult.failure(COMM_CHECK_EXECUTION_ERROR, "Unexpected communication check execution failure");
        }
    }

    private String resolveFromLocalEvidence(
            String sourceLeadId,
            Map<String, Object> resolvedConfig,
            Map<String, Object> rawConfig) {
        int lookbackMinutes = resolveLookbackMinutes(resolvedConfig, rawConfig);
        OffsetDateTime since = OffsetDateTime.now(clock).minusMinutes(lookbackMinutes);
        List<ProcessedCallEntity> candidateCalls =
                processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        sourceLeadId, since);
        for (ProcessedCallEntity candidate : candidateCalls) {
            String classification = classifyCall(candidate);
            if (classification != null) {
                return classification;
            }
        }
        return null;
    }

    private int resolveLookbackMinutes(Map<String, Object> resolvedConfig, Map<String, Object> rawConfig) {
        Integer configured = parseLookbackMinutes(resolvedConfig, "lookbackMinutes");
        if (configured == null) {
            configured = parseLookbackMinutes(rawConfig, "lookbackMinutes");
        }
        if (configured == null) {
            return DEFAULT_LOOKBACK_MINUTES;
        }
        return Math.max(MIN_LOOKBACK_MINUTES, configured);
    }

    private Integer parseLookbackMinutes(Map<String, Object> config, String key) {
        if (config == null) {
            return null;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String classifyCall(ProcessedCallEntity call) {
        Integer threshold = callOutcomeRulesProperties.getShortCallThresholdSeconds();
        int shortCallThreshold = threshold == null ? 0 : threshold;
        Integer durationSeconds = call.getDurationSeconds();

        if (durationSeconds != null) {
            if (durationSeconds > shortCallThreshold) {
                return CONVERSATIONAL;
            }
            if (durationSeconds > 0) {
                return CONNECTED_NON_CONVERSATIONAL;
            }
            if (durationSeconds == 0) {
                return COMM_NOT_FOUND;
            }
        }

        String normalizedOutcome = normalizeOutcome(call.getOutcome());
        if (isNonConnectedOutcome(normalizedOutcome)) {
            return COMM_NOT_FOUND;
        }
        return null;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) {
            return null;
        }
        String trimmed = outcome.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private boolean isNonConnectedOutcome(String normalizedOutcome) {
        if (normalizedOutcome == null) {
            return false;
        }
        return Set.of(
                        "no answer",
                        "busy",
                        "voicemail",
                        "left voicemail",
                        "not connected",
                        "missed",
                        "missed call",
                        "failed",
                        "canceled",
                        "cancelled")
                .contains(normalizedOutcome);
    }
}
