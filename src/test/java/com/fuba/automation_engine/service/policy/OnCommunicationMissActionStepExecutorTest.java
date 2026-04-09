package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnCommunicationMissActionStepExecutorTest {

    private FollowUpBossClient followUpBossClient;
    private OnCommunicationMissActionStepExecutor executor;

    @BeforeEach
    void setUp() {
        followUpBossClient = mock(FollowUpBossClient.class);
        executor = new OnCommunicationMissActionStepExecutor(followUpBossClient);
    }

    @Test
    void shouldSucceedForReassignWhenTargetUserConfigured() {
        when(followUpBossClient.reassignPerson(901L, 12L)).thenReturn(ActionExecutionResult.ok());
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "REASSIGN", "targetUserId", 12))));

        assertEquals(true, result.success());
        assertEquals(PolicyStepResultCode.ACTION_SUCCESS, result.resultCode());
        verify(followUpBossClient).reassignPerson(901L, 12L);
    }

    @Test
    void shouldSucceedForMoveToPondWhenTargetPondConfigured() {
        when(followUpBossClient.movePersonToPond(901L, 44L)).thenReturn(ActionExecutionResult.ok());
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "MOVE_TO_POND", "targetPondId", 44))));

        assertEquals(true, result.success());
        assertEquals(PolicyStepResultCode.ACTION_SUCCESS, result.resultCode());
        verify(followUpBossClient).movePersonToPond(901L, 44L);
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

    @Test
    void shouldFailWhenReassignTargetMissing() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "REASSIGN"))));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TARGET_MISSING, result.reasonCode());
    }

    @Test
    void shouldFailWhenMoveToPondTargetMissing() {
        PolicyStepExecutionResult result = executor.execute(context(Map.of(
                "actionConfig", Map.of("actionType", "MOVE_TO_POND"))));

        assertEquals(false, result.success());
        assertEquals(OnCommunicationMissActionStepExecutor.ACTION_TARGET_MISSING, result.reasonCode());
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
