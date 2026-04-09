package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OnCommunicationMissActionStepExecutor implements PolicyStepExecutor {
    // This step is a downstream dependency of WAIT_AND_CHECK_COMMUNICATION and should
    // only run on the COMM_NOT_FOUND branch (see PolicyStepTransitionContract).

    static final String ACTION_CONFIG_MISSING = "ACTION_CONFIG_MISSING";
    static final String ACTION_TYPE_MISSING = "ACTION_TYPE_MISSING";
    static final String ACTION_TYPE_UNSUPPORTED = "ACTION_TYPE_UNSUPPORTED";
    static final String ACTION_TARGET_MISSING = "ACTION_TARGET_MISSING";
    static final String ACTION_TARGET_INVALID = "ACTION_TARGET_INVALID";
    static final String ACTION_EXECUTION_ERROR = "ACTION_EXECUTION_ERROR";
    static final String SOURCE_LEAD_ID_MISSING = "SOURCE_LEAD_ID_MISSING";
    static final String SOURCE_LEAD_ID_INVALID = "SOURCE_LEAD_ID_INVALID";

    private static final Logger log = LoggerFactory.getLogger(OnCommunicationMissActionStepExecutor.class);
    private static final String FIELD_ACTION_CONFIG = "actionConfig";
    private static final String FIELD_ACTION_TYPE = "actionType";
    private static final String FIELD_TARGET_USER_ID = "targetUserId";
    private static final String FIELD_TARGET_POND_ID = "targetPondId";
    private static final String ACTION_TYPE_REASSIGN = "REASSIGN";
    private static final String ACTION_TYPE_MOVE_TO_POND = "MOVE_TO_POND";
    private final FollowUpBossClient followUpBossClient;

    public OnCommunicationMissActionStepExecutor(FollowUpBossClient followUpBossClient) {
        this.followUpBossClient = followUpBossClient;
    }

    @Override
    public boolean supports(PolicyStepType stepType) {
        return stepType == PolicyStepType.ON_FAILURE_EXECUTE_ACTION;
    }

    @Override
    public PolicyStepExecutionResult execute(PolicyStepExecutionContext context) {
        try {
            ActionConfig actionConfig = extractActionConfig(context.policyBlueprintSnapshot());
            if (actionConfig == null) {
                return PolicyStepExecutionResult.failure(
                        ACTION_CONFIG_MISSING,
                        "Missing actionConfig for ON_FAILURE_EXECUTE_ACTION");
            }
            if (actionConfig.actionType().isBlank()) {
                return PolicyStepExecutionResult.failure(
                        ACTION_TYPE_MISSING,
                        "Missing actionType for ON_FAILURE_EXECUTE_ACTION");
            }
            if (!ACTION_TYPE_REASSIGN.equals(actionConfig.actionType())
                    && !ACTION_TYPE_MOVE_TO_POND.equals(actionConfig.actionType())) {
                return PolicyStepExecutionResult.failure(
                        ACTION_TYPE_UNSUPPORTED,
                        "Unsupported actionType for ON_FAILURE_EXECUTE_ACTION: " + actionConfig.actionType());
            }
            if (context.sourceLeadId() == null || context.sourceLeadId().isBlank()) {
                return PolicyStepExecutionResult.failure(SOURCE_LEAD_ID_MISSING, "Missing sourceLeadId for ON_FAILURE_EXECUTE_ACTION");
            }
            long personId;
            try {
                personId = Long.parseLong(context.sourceLeadId().trim());
            } catch (NumberFormatException ex) {
                return PolicyStepExecutionResult.failure(SOURCE_LEAD_ID_INVALID, "Invalid sourceLeadId for ON_FAILURE_EXECUTE_ACTION");
            }

            ActionExecutionResult actionResult;
            if (ACTION_TYPE_REASSIGN.equals(actionConfig.actionType())) {
                if (actionConfig.targetUserId() == null) {
                    return PolicyStepExecutionResult.failure(
                            ACTION_TARGET_MISSING,
                            "Missing targetUserId for REASSIGN action");
                }
                if (actionConfig.targetUserId() <= 0) {
                    return PolicyStepExecutionResult.failure(
                            ACTION_TARGET_INVALID,
                            "Invalid targetUserId for REASSIGN action");
                }
                actionResult = followUpBossClient.reassignPerson(personId, actionConfig.targetUserId());
            } else {
                if (actionConfig.targetPondId() == null) {
                    return PolicyStepExecutionResult.failure(
                            ACTION_TARGET_MISSING,
                            "Missing targetPondId for MOVE_TO_POND action");
                }
                if (actionConfig.targetPondId() <= 0) {
                    return PolicyStepExecutionResult.failure(
                            ACTION_TARGET_INVALID,
                            "Invalid targetPondId for MOVE_TO_POND action");
                }
                actionResult = followUpBossClient.movePersonToPond(personId, actionConfig.targetPondId());
            }
            if (actionResult == null || !actionResult.success()) {
                String reason = actionResult == null || actionResult.reasonCode() == null || actionResult.reasonCode().isBlank()
                        ? ACTION_EXECUTION_ERROR
                        : actionResult.reasonCode();
                String message = actionResult == null || actionResult.message() == null || actionResult.message().isBlank()
                        ? "Action execution returned unsuccessful result"
                        : actionResult.message();
                return PolicyStepExecutionResult.failure(reason, message);
            }
            return PolicyStepExecutionResult.success(PolicyStepResultCode.ACTION_SUCCESS);
        } catch (RuntimeException ex) {
            log.error(
                    "Unexpected on-failure action execution error stepId={} runId={} sourceLeadId={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    ex);
            return PolicyStepExecutionResult.failure(
                    ACTION_EXECUTION_ERROR,
                    "Unexpected on-failure action execution error");
        }
    }

    @SuppressWarnings("unchecked")
    private ActionConfig extractActionConfig(Map<String, Object> blueprint) {
        if (blueprint == null || blueprint.isEmpty()) {
            return null;
        }
        Object actionConfigObject = blueprint.get(FIELD_ACTION_CONFIG);
        if (!(actionConfigObject instanceof Map<?, ?> rawActionConfig)) {
            return null;
        }
        Map<String, Object> actionConfig = (Map<String, Object>) rawActionConfig;
        Object actionTypeObject = actionConfig.get(FIELD_ACTION_TYPE);
        String actionType = actionTypeObject instanceof String actionTypeText
                ? actionTypeText.trim().toUpperCase(Locale.ROOT)
                : "";
        return new ActionConfig(
                actionType,
                parseLong(actionConfig.get(FIELD_TARGET_USER_ID)),
                parseLong(actionConfig.get(FIELD_TARGET_POND_ID)));
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

    private record ActionConfig(String actionType, Long targetUserId, Long targetPondId) {
    }
}
