package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitAndCheckClaimStepExecutorTest {

    private FollowUpBossClient followUpBossClient;
    private WaitAndCheckClaimStepExecutor executor;

    @BeforeEach
    void setUp() {
        followUpBossClient = mock(FollowUpBossClient.class);
        FubRetryProperties retryProperties = new FubRetryProperties();
        retryProperties.setMaxAttempts(2);
        executor = new WaitAndCheckClaimStepExecutor(followUpBossClient, retryProperties);
    }

    @Test
    void shouldResolveClaimedWhenClaimedFieldIsTrue() {
        when(followUpBossClient.getPersonById(798L)).thenReturn(new PersonDetails(798L, true, null));

        PolicyStepExecutionResult result = executor.execute(context("798"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.CLAIMED, result.resultCode());
    }

    @Test
    void shouldResolveNotClaimedWhenClaimedFieldIsFalse() {
        when(followUpBossClient.getPersonById(799L)).thenReturn(new PersonDetails(799L, false, 10L));

        PolicyStepExecutionResult result = executor.execute(context("799"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.NOT_CLAIMED, result.resultCode());
    }

    @Test
    void shouldFallbackToAssignedUserIdWhenClaimedFieldIsMissing() {
        when(followUpBossClient.getPersonById(800L)).thenReturn(new PersonDetails(800L, null, 5L));

        PolicyStepExecutionResult result = executor.execute(context("800"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.CLAIMED, result.resultCode());
    }

    @Test
    void shouldReturnNotClaimedWhenClaimedMissingAndAssignedUserIdZero() {
        when(followUpBossClient.getPersonById(801L)).thenReturn(new PersonDetails(801L, null, 0L));

        PolicyStepExecutionResult result = executor.execute(context("801"));

        assertTrue(result.success());
        assertEquals(PolicyStepResultCode.NOT_CLAIMED, result.resultCode());
    }

    @Test
    void shouldFailWhenSourceLeadIdIsInvalid() {
        PolicyStepExecutionResult result = executor.execute(context("abc"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckClaimStepExecutor.SOURCE_LEAD_ID_INVALID, result.reasonCode());
    }

    @Test
    void shouldFailWhenTransientReadExhaustsRetries() {
        when(followUpBossClient.getPersonById(802L))
                .thenThrow(new FubTransientException("transient", 503))
                .thenThrow(new FubTransientException("transient", 503));

        PolicyStepExecutionResult result = executor.execute(context("802"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckClaimStepExecutor.FUB_PERSON_READ_TRANSIENT, result.reasonCode());
        verify(followUpBossClient, times(2)).getPersonById(802L);
    }

    @Test
    void shouldFailWhenPermanentReadFails() {
        when(followUpBossClient.getPersonById(803L))
                .thenThrow(new FubPermanentException("permanent", 400));

        PolicyStepExecutionResult result = executor.execute(context("803"));

        assertEquals(false, result.success());
        assertEquals(WaitAndCheckClaimStepExecutor.FUB_PERSON_READ_PERMANENT, result.reasonCode());
    }

    private PolicyStepExecutionContext context(String sourceLeadId) {
        PolicyExecutionStepClaimRepository.ClaimedStepRow row = new PolicyExecutionStepClaimRepository.ClaimedStepRow(
                101L,
                201L,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                1,
                null,
                PolicyExecutionStepStatus.PROCESSING);
        return new PolicyStepExecutionContext(
                101L,
                201L,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                WebhookSource.FUB,
                sourceLeadId,
                row);
    }
}
