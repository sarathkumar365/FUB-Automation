package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnCommunicationMissActionStepExecutorTest {

    private final OnCommunicationMissActionStepExecutor executor = new OnCommunicationMissActionStepExecutor();

    @Test
    void shouldReturnSuccessForReassignInNoopMode() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "REASSIGN"))));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.ACTION_SUCCESS, result.resultCode());
    }

    @Test
    void shouldReturnSuccessForMoveToPondInNoopMode() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "MOVE_TO_POND"))));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.ACTION_SUCCESS, result.resultCode());
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
