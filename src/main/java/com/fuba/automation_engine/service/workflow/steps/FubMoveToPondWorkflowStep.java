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
public class FubMoveToPondWorkflowStep implements WorkflowStepType {

    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";
    static final String TARGET_POND_ID_MISSING = "TARGET_POND_ID_MISSING";
    static final String TARGET_POND_ID_INVALID = "TARGET_POND_ID_INVALID";
    static final String FUB_MOVE_TRANSIENT = "FUB_MOVE_TRANSIENT";
    static final String FUB_MOVE_PERMANENT = "FUB_MOVE_PERMANENT";
    static final String MOVE_EXECUTION_ERROR = "MOVE_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(FubMoveToPondWorkflowStep.class);

    private final FollowUpBossClient followUpBossClient;
    private final FubCallHelper fubCallHelper;

    public FubMoveToPondWorkflowStep(FollowUpBossClient followUpBossClient, FubCallHelper fubCallHelper) {
        this.followUpBossClient = followUpBossClient;
        this.fubCallHelper = fubCallHelper;
    }

    @Override
    public String id() {
        return "fub_move_to_pond";
    }

    @Override
    public String displayName() {
        return "Move Lead to Pond";
    }

    @Override
    public String description() {
        return "Move a lead to a specified pond in Follow Up Boss.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "targetPondId", Map.of(
                                "type", "integer",
                                "description", "FUB pond ID to move the lead to. Accepts template expressions.")),
                "required", java.util.List.of("targetPondId"));
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
        Long targetPondId = parseLong(config.get("targetPondId"));
        if (targetPondId == null) {
            return StepExecutionResult.failure(TARGET_POND_ID_MISSING, "Missing targetPondId in config");
        }
        if (targetPondId <= 0) {
            return StepExecutionResult.failure(TARGET_POND_ID_INVALID, "Invalid targetPondId: " + targetPondId);
        }

        try {
            ActionExecutionResult actionResult =
                    fubCallHelper.executeWithRetry(() -> followUpBossClient.movePersonToPond(personId, targetPondId));
            if (actionResult == null || !actionResult.success()) {
                String message = actionResult != null && actionResult.message() != null
                        ? actionResult.message() : "Move to pond action returned unsuccessful result";
                return StepExecutionResult.failure("FAILED", message);
            }
            return StepExecutionResult.success("SUCCESS");
        } catch (FubTransientException ex) {
            return StepExecutionResult.transientFailure(FUB_MOVE_TRANSIENT,
                    "Transient failure moving person " + personId + " to pond"
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (FubPermanentException ex) {
            return StepExecutionResult.failure(FUB_MOVE_PERMANENT,
                    "Permanent failure moving person " + personId + " to pond"
                            + " status=" + FubCallHelper.stringifyStatus(ex.getStatusCode()));
        } catch (RuntimeException ex) {
            log.error("Unexpected move-to-pond execution failure stepId={} runId={} sourceLeadId={}",
                    context.stepId(), context.runId(), context.sourceLeadId(), ex);
            return StepExecutionResult.failure(MOVE_EXECUTION_ERROR, "Unexpected move-to-pond execution failure");
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
