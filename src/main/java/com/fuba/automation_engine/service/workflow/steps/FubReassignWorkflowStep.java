package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.fub.FubCallHelper;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FubReassignWorkflowStep implements WorkflowStepType {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String TARGET_USER_ID_MISSING = "TARGET_USER_ID_MISSING";
    static final String TARGET_USER_ID_INVALID = "TARGET_USER_ID_INVALID";
    static final String FUB_REASSIGN_TRANSIENT = "FUB_REASSIGN_TRANSIENT";
    static final String FUB_REASSIGN_PERMANENT = "FUB_REASSIGN_PERMANENT";
    static final String REASSIGN_EXECUTION_ERROR = "REASSIGN_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(FubReassignWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public FubReassignWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "fub_reassign";
    }

    @Override
    public String displayName() {
        return "Reassign Lead";
    }

    @Override
    public String description() {
        return "Reassign a lead to a different agent in Follow Up Boss.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "targetUserId", Map.of(
                                "type", "integer",
                                "description", "FUB user ID to reassign the lead to. Accepts template expressions.")),
                "required", java.util.List.of("targetUserId"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("SUCCESS", "FAILED");
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

        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        Long targetUserId = parseLong(config.get("targetUserId"));
        if (targetUserId == null) {
            return StepExecutionResult.failure(TARGET_USER_ID_MISSING, "Missing targetUserId in config");
        }
        if (targetUserId <= 0) {
            return StepExecutionResult.failure(TARGET_USER_ID_INVALID, "Invalid targetUserId: " + targetUserId);
        }

        try {
            ActionExecutionResult actionResult =
                    fubCallHelper.executeWithRetry(() -> followUpBossClient.reassignPerson(personId, targetUserId));
            if (actionResult == null || !actionResult.success()) {
                String message = actionResult != null && actionResult.message() != null
                        ? actionResult.message() : "Reassign action returned unsuccessful result";
                return StepExecutionResult.failure("FAILED", message);
            }
            return StepExecutionResult.success("SUCCESS");
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(FUB_REASSIGN_TRANSIENT,
                    "Transient failure reassigning person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(FUB_REASSIGN_PERMANENT,
                    "Permanent failure reassigning person " + personId
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected reassign execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(), context.runId(), context.sourceLeadId(), ex);
            return StepExecutionResult.failure(REASSIGN_EXECUTION_ERROR, "Unexpected reassign execution failure");
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
