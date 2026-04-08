package com.fuba.automation_engine.service.policy;

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
    static final String ACTION_EXECUTION_ERROR = "ACTION_EXECUTION_ERROR";

    private static final Logger log = LoggerFactory.getLogger(OnCommunicationMissActionStepExecutor.class);
    private static final String FIELD_ACTION_CONFIG = "actionConfig";
    private static final String FIELD_ACTION_TYPE = "actionType";
    private static final String ACTION_TYPE_REASSIGN = "REASSIGN";
    private static final String ACTION_TYPE_MOVE_TO_POND = "MOVE_TO_POND";

    @Override
    public boolean supports(PolicyStepType stepType) {
        return stepType == PolicyStepType.ON_FAILURE_EXECUTE_ACTION;
    }

    @Override
    public PolicyStepExecutionResult execute(PolicyStepExecutionContext context) {
        try {
            String actionType = extractActionType(context.policyBlueprintSnapshot());
            if (actionType == null) {
                return PolicyStepExecutionResult.failure(
                        ACTION_CONFIG_MISSING,
                    "Missing actionConfig for ON_FAILURE_EXECUTE_ACTION");
            }
            if (actionType.isBlank()) {
                return PolicyStepExecutionResult.failure(
                        ACTION_TYPE_MISSING,
                    "Missing actionType for ON_FAILURE_EXECUTE_ACTION");
            }
            if (!ACTION_TYPE_REASSIGN.equals(actionType) && !ACTION_TYPE_MOVE_TO_POND.equals(actionType)) {
                return PolicyStepExecutionResult.failure(
                        ACTION_TYPE_UNSUPPORTED,
                    "Unsupported actionType for ON_FAILURE_EXECUTE_ACTION: " + actionType);
            }

            // Step 2 scope: no provider mutation yet. Record intent and continue.
            log.info(
                    "ON_FAILURE_EXECUTE_ACTION no-op executed stepId={} runId={} sourceLeadId={} actionType={}",
                    context.stepId(),
                    context.runId(),
                    context.sourceLeadId(),
                    actionType);
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
    private String extractActionType(Map<String, Object> blueprint) {
        if (blueprint == null || blueprint.isEmpty()) {
            return null;
        }
        Object actionConfigObject = blueprint.get(FIELD_ACTION_CONFIG);
        if (!(actionConfigObject instanceof Map<?, ?> rawActionConfig)) {
            return null;
        }
        Map<String, Object> actionConfig = (Map<String, Object>) rawActionConfig;
        Object actionTypeObject = actionConfig.get(FIELD_ACTION_TYPE);
        if (!(actionTypeObject instanceof String actionType)) {
            return "";
        }
        return actionType.trim().toUpperCase(Locale.ROOT);
    }
}
