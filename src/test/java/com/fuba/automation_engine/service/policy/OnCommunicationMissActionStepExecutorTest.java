package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnCommunicationMissActionStepExecutorTest {

    private final OnCommunicationMissActionStepExecutor executor = new OnCommunicationMissActionStepExecutor();

    @Test
    void shouldFailExplicitlyForReassignWhileActionTargetIsUndecided() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "REASSIGN"))));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TARGET_UNCONFIGURED, result.reasonCode());
    }

    @Test
    void shouldFailExplicitlyForMoveToPondWhileActionTargetIsUndecided() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "MOVE_TO_POND"))));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TARGET_UNCONFIGURED, result.reasonCode());
    }

    @Test
    void shouldFailWhenActionConfigMissing() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of()));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_CONFIG_MISSING, result.reasonCode());
    }

    @Test
    void shouldFailWhenActionTypeMissing() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of())));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TYPE_MISSING, result.reasonCode());
    }

    @Test
    void shouldFailWhenActionTypeUnsupported() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "UNKNOWN"))));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TYPE_UNSUPPORTED, result.reasonCode());
    }

    private PolicyStepExecutionContext context(Map<String, Object> blueprint) {
        PolicyExecutionStepClaimRepository.ClaimedStepRow row = new PolicyExecutionStepClaimRepository.ClaimedStepRow(
                120L,
                220L,
                PolicyStepType.ON_FAILURE_EXECUTE_ACTION,
                3,
                null,
                PolicyExecutionStepStatus.PROCESSING);
        return new PolicyStepExecutionContext(
                120L,
                220L,
                PolicyStepType.ON_FAILURE_EXECUTE_ACTION,
                WebhookSource.FUB,
                "901",
                blueprint,
                row);
    }
}
