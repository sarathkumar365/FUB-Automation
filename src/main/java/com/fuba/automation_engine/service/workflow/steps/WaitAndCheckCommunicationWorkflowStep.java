package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.CallEvidence;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

    // Floor on the lookback window. Covers webhook-delivery race + "agent called before claiming" pattern.
    private static final int DEFAULT_BUFFER_MINUTES = 5;

    // Outcome strings (case-insensitive) that classify a call as non-connected.
    private static final Set<String> NON_CONNECTED_OUTCOMES = Set.of(
            "no answer", "busy", "voicemail", "left voicemail", "not connected",
            "missed", "missed call", "failed", "canceled", "cancelled");

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
        return "Wait for a specified duration, then classify call evidence in the window since the run started "
                + "(local DB first, FUB fallback). Configurable lookbackMinutes extends the window further back.";
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
                                "minimum", 0,
                                "description",
                                "Minutes before the run's start time to also include in the window. "
                                        + "Default behavior covers from runStart minus a 5-minute buffer.")));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of(CONVERSATIONAL, CONNECTED_NON_CONVERSATIONAL, COMM_NOT_FOUND);
    }

    @Override
    public RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.DEFAULT_FUB;
    }

    // Flow:
    //   1. Parse the FUB person id from sourceLeadId.
    //   2. Compute the detection window [since, now], anchored to the run's start time (known-issues #21).
    //   3. Look at local processed_calls in that window. If any call is decisive, return.
    //   4. Otherwise fall back to FUB's /v1/calls. Filter the response to the window. Classify.
    //   5. If neither path produces a decision, the lead has no qualifying communication → COMM_NOT_FOUND.
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
            OffsetDateTime since = computeLookbackSince(
                    runStartedAtFrom(context), context.resolvedConfig(), context.rawConfig());

            // Primary: local DB evidence (already filtered by `since` server-side).
            String localDecision = classifyFirstDecisive(loadLocalEvidence(normalizedSourceLeadId, since));
            if (localDecision != null) {
                return StepExecutionResult.success(localDecision);
            }

            // Fallback: FUB /v1/calls. Returns up to 10 most recent calls; we narrow to the window client-side.
            List<CallEvidence> fubEvidence = fubCallHelper.executeWithRetry(
                    () -> followUpBossClient.listPersonCalls(personId));
            String fubDecision = classifyFirstDecisive(narrowToWindow(fubEvidence, since));
            return StepExecutionResult.success(fubDecision != null ? fubDecision : COMM_NOT_FOUND);
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

    // Window-start computation. Anchor: runStartedAt (stable across long waits). Floor: 5-min buffer
    // covers webhook-delivery races and "agent called before claiming" patterns. Configured
    // `lookbackMinutes` extends the window further back than the buffer when set.
    private OffsetDateTime computeLookbackSince(
            OffsetDateTime runStartedAt,
            Map<String, Object> resolvedConfig,
            Map<String, Object> rawConfig) {
        Integer configured = parseLookbackMinutes(resolvedConfig, "lookbackMinutes");
        if (configured == null) {
            configured = parseLookbackMinutes(rawConfig, "lookbackMinutes");
        }
        int effectiveMinutes = Math.max(configured == null ? 0 : configured, DEFAULT_BUFFER_MINUTES);
        OffsetDateTime anchor = runStartedAt != null ? runStartedAt : OffsetDateTime.now(clock);
        return anchor.minus(Duration.ofMinutes(effectiveMinutes));
    }

    // Defensive: any caller that builds StepExecutionContext outside the run repository path may pass null.
    private OffsetDateTime runStartedAtFrom(StepExecutionContext context) {
        if (context.runContext() == null || context.runContext().metadata() == null) return null;
        return context.runContext().metadata().runStartedAt();
    }

    // Local path: DB query already filters by `since` server-side; just map rows to CallEvidence.
    private List<CallEvidence> loadLocalEvidence(String sourceLeadId, OffsetDateTime since) {
        List<ProcessedCallEntity> rows =
                processedCallRepository.findTop10BySourceLeadIdAndCallStartedAtGreaterThanEqualOrderByCallStartedAtDescIdDesc(
                        sourceLeadId, since);
        List<CallEvidence> result = new ArrayList<>(rows.size());
        for (ProcessedCallEntity row : rows) {
            result.add(new CallEvidence(
                    row.getSourceLeadId(),
                    row.getCallStartedAt(),
                    row.getDurationSeconds(),
                    row.getOutcome(),
                    Boolean.TRUE.equals(row.getIsIncoming())));
        }
        return result;
    }

    // FUB-only filter. FUB's /v1/calls silently ignores documented date filters (since=, createdSince=,
    // etc. — verified empirically), so we narrow to the window client-side. Local path doesn't need this
    // because the DB query handles `since` directly.
    private List<CallEvidence> narrowToWindow(List<CallEvidence> evidence, OffsetDateTime since) {
        if (evidence == null || since == null) return evidence;
        List<CallEvidence> filtered = new ArrayList<>(evidence.size());
        for (CallEvidence call : evidence) {
            if (call.callStartedAt() == null) continue;
            if (call.callStartedAt().isBefore(since)) continue;
            filtered.add(call);
        }
        return filtered;
    }

    // Returns the first decisive classification, or null if no call in the list yielded one.
    private String classifyFirstDecisive(List<CallEvidence> evidence) {
        if (evidence == null) return null;
        for (CallEvidence call : evidence) {
            String classification = classifyCall(call);
            if (classification != null) return classification;
        }
        return null;
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

    // Returns one of CONVERSATIONAL / CONNECTED_NON_CONVERSATIONAL / COMM_NOT_FOUND, or null
    // meaning "this call gives no signal — let the caller try the next one." The null path is hit
    // when both duration and outcome are unknown (e.g. an in-flight call record).
    private String classifyCall(CallEvidence call) {
        Integer threshold = callOutcomeRulesProperties.getShortCallThresholdSeconds();
        int shortCallThreshold = threshold == null ? 0 : threshold;
        Integer durationSeconds = call.durationSeconds();

        if (durationSeconds != null) {
            if (durationSeconds > shortCallThreshold) return CONVERSATIONAL;
            if (durationSeconds > 0) return CONNECTED_NON_CONVERSATIONAL;
            if (durationSeconds == 0) return COMM_NOT_FOUND;
        }

        String normalizedOutcome = normalizeOutcome(call.outcome());
        if (normalizedOutcome != null && NON_CONNECTED_OUTCOMES.contains(normalizedOutcome)) {
            return COMM_NOT_FOUND;
        }
        return null;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) return null;
        String trimmed = outcome.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }
}
