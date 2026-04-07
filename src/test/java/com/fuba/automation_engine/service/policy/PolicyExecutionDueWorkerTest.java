package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.config.PolicyWorkerProperties;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyExecutionDueWorkerTest {

    @Test
    void shouldApplyGuardrailsForInvalidConfiguredLimits() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(0);
        properties.setMaxStepsPerPoll(0);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        Clock clock = Clock.systemUTC();
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, clock);
        PolicyExecutionDueWorker.PollGuardrails guardrails = worker.resolveEffectiveGuardrails();

        assertEquals(1, guardrails.claimBatchSize());
        assertEquals(1, guardrails.maxStepsPerPoll());
    }

    @Test
    void shouldRunScaffoldPollWithoutSideEffects() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(5);
        properties.setMaxStepsPerPoll(20);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        when(claimRepository.claimDuePendingSteps(any(), eq(5))).thenReturn(List.of());
        Clock clock = Clock.systemUTC();
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, clock);
        assertDoesNotThrow(worker::pollAndProcessDueSteps);
        verify(claimRepository).claimDuePendingSteps(any(), eq(5));
    }

    @Test
    void shouldRunMultipleClaimCyclesUntilNoRowsOrBudgetExhausted() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(2);
        properties.setMaxStepsPerPoll(5);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        when(claimRepository.claimDuePendingSteps(any(), eq(2)))
                .thenReturn(List.of(
                        row(1L, 11L, 1),
                        row(2L, 11L, 2)))
                .thenReturn(List.of(
                        row(3L, 12L, 1),
                        row(4L, 12L, 2)))
                .thenReturn(List.of(row(5L, 13L, 1)));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, fixedClock);
        worker.pollAndProcessDueSteps();

        verify(claimRepository, times(2)).claimDuePendingSteps(any(), eq(2));
        verify(claimRepository, times(1)).claimDuePendingSteps(any(), eq(1));
    }

    @Test
    void shouldStopWhenClaimRepositoryReturnsEmptyRows() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(9);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, fixedClock);
        worker.pollAndProcessDueSteps();

        verify(claimRepository, times(1)).claimDuePendingSteps(any(), eq(3));
    }

    private PolicyExecutionStepClaimRepository.ClaimedStepRow row(long id, long runId, int stepOrder) {
        return new PolicyExecutionStepClaimRepository.ClaimedStepRow(
                id,
                runId,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                stepOrder,
                null,
                PolicyExecutionStepStatus.PROCESSING);
    }
}
