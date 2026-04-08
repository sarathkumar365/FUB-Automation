package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitAndCheckCommunicationStepExecutorTest {

    private FollowUpBossClient followUpBossClient;
    private WaitAndCheckCommunicationStepExecutor executor;

    @BeforeEach
    void setUp() {
        followUpBossClient = mock(FollowUpBossClient.class);
        FubRetryProperties retryProperties = new FubRetryProperties();
        retryProperties.setMaxAttempts(2);
        executor = new WaitAndCheckCommunicationStepExecutor(followUpBossClient, retryProperties);
    }

    @Test
    void shouldReturnCommFoundWhenCommunicationExists() {
        when(followUpBossClient.checkPersonCommunication(901L))
                .thenReturn(new PersonCommunicationCheckResult(901L, true));

        PolicyStepExecutionResult result = executor.execute(context("901"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.COMM_FOUND, result.resultCode());
    }

    @Test
    void shouldReturnCommNotFoundWhenCommunicationIsAbsent() {
        when(followUpBossClient.checkPersonCommunication(902L))
                .thenReturn(new PersonCommunicationCheckResult(902L, false));

        PolicyStepExecutionResult result = executor.execute(context("902"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.COMM_NOT_FOUND, result.resultCode());
    }

    @Test
    void shouldFailWhenSourceLeadIdMissing() {
        PolicyStepExecutionResult result = executor.execute(context(" "));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckCommunicationStepExecutor.SOURCE_LEAD_ID_MISSING, result.reasonCode());
    }

    @Test
    void shouldFailWhenSourceLeadIdInvalid() {
        PolicyStepExecutionResult result = executor.execute(context("abc"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckCommunicationStepExecutor.SOURCE_LEAD_ID_INVALID, result.reasonCode());
    }

    @Test
    void shouldFailWhenTransientReadExhaustsRetries() {
        when(followUpBossClient.checkPersonCommunication(903L))
                .thenThrow(new FubTransientException("transient", 503))
                .thenThrow(new FubTransientException("transient", 503));

        PolicyStepExecutionResult result = executor.execute(context("903"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckCommunicationStepExecutor.FUB_COMM_CHECK_TRANSIENT, result.reasonCode());
        verify(followUpBossClient, times(2)).checkPersonCommunication(903L);
    }

    @Test
    void shouldFailWhenPermanentReadFails() {
        when(followUpBossClient.checkPersonCommunication(904L))
                .thenThrow(new FubPermanentException("permanent", 400));

        PolicyStepExecutionResult result = executor.execute(context("904"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckCommunicationStepExecutor.FUB_COMM_CHECK_PERMANENT, result.reasonCode());
    }

    private PolicyStepExecutionContext context(String sourceLeadId) {
        PolicyExecutionStepClaimRepository.ClaimedStepRow row = new PolicyExecutionStepClaimRepository.ClaimedStepRow(
                110L,
                210L,
                PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                2,
                null,
                PolicyExecutionStepStatus.PROCESSING);
        return new PolicyStepExecutionContext(
                110L,
                210L,
                PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                WebhookSource.FUB,
                sourceLeadId,
                Map.of(),
                row);
    }
}
