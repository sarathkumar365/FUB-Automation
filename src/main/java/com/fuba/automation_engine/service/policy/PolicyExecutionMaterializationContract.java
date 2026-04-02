package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import java.util.List;

public final class PolicyExecutionMaterializationContract {

    private PolicyExecutionMaterializationContract() {
    }

    public static final int CLAIM_STEP_ORDER = 1;
    public static final int COMMUNICATION_STEP_ORDER = 2;
    public static final int ACTION_STEP_ORDER = 3;

    public static List<StepTemplate> initialTemplates() {
        return List.of(
                new StepTemplate(
                        CLAIM_STEP_ORDER,
                        PolicyStepType.WAIT_AND_CHECK_CLAIM,
                        PolicyExecutionStepStatus.PENDING,
                        null),
                new StepTemplate(
                        COMMUNICATION_STEP_ORDER,
                        PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                        PolicyExecutionStepStatus.WAITING_DEPENDENCY,
                        CLAIM_STEP_ORDER),
                new StepTemplate(
                        ACTION_STEP_ORDER,
                        PolicyStepType.ON_FAILURE_EXECUTE_ACTION,
                        PolicyExecutionStepStatus.WAITING_DEPENDENCY,
                        COMMUNICATION_STEP_ORDER));
    }

    public record StepTemplate(
            int stepOrder,
            PolicyStepType stepType,
            PolicyExecutionStepStatus initialStatus,
            Integer dependsOnStepOrder) {
    }
}
