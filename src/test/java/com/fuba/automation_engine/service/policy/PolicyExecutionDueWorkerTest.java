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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyExecutionDueWorkerTest {

    @Test
    void shouldApplyGuardrailsForInvalidConfiguredLimits() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(0);
        properties.setMaxStepsPerPoll(0);
        properties.setStaleProcessingTimeoutMinutes(0);
        properties.setStaleProcessingRequeueLimit(-2);
        properties.setStaleProcessingBatchSize(0);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        Clock clock = Clock.systemUTC();
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, clock);
        PolicyExecutionDueWorker.PollGuardrails guardrails = worker.resolveEffectiveGuardrails();
        PolicyExecutionDueWorker.StaleRecoveryGuardrails staleGuardrails = worker.resolveStaleRecoveryGuardrails();

        assertEquals(1, guardrails.claimBatchSize());
        assertEquals(1, guardrails.maxStepsPerPoll());
        assertEquals(1, staleGuardrails.timeoutMinutes());
        assertEquals(0, staleGuardrails.requeueLimit());
        assertEquals(1, staleGuardrails.batchSize());
    }

    @Test
    void shouldRunScaffoldPollWithoutSideEffects() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(5);
        properties.setMaxStepsPerPoll(20);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(5))).thenReturn(List.of());
        Clock clock = Clock.systemUTC();
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, clock);
        assertDoesNotThrow(worker::pollAndProcessDueSteps);
        verify(claimRepository).claimDuePendingSteps(any(), eq(5));
    }

    @Test
    void shouldRunMultipleClaimCyclesUntilNoRowsOrBudgetExhausted() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(2);
        properties.setMaxStepsPerPoll(5);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(2)))
                .thenReturn(List.of(
                        row(1L, 11L, 1),
                        row(2L, 11L, 2)))
                .thenReturn(List.of(
                        row(3L, 12L, 1),
                        row(4L, 12L, 2)));
        when(claimRepository.claimDuePendingSteps(any(), eq(1)))
                .thenReturn(List.of(row(5L, 13L, 1)));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);
        worker.pollAndProcessDueSteps();

        verify(claimRepository, times(2)).claimDuePendingSteps(any(), eq(2));
        verify(claimRepository, times(1)).claimDuePendingSteps(any(), eq(1));
        verify(stepExecutionService, times(5)).executeClaimedStep(any());
    }

    @Test
    void shouldStopWhenClaimRepositoryReturnsEmptyRows() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(9);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);
        worker.pollAndProcessDueSteps();

        verify(claimRepository, times(1)).claimDuePendingSteps(any(), eq(3));
        verify(stepExecutionService, times(0)).executeClaimedStep(any());
    }

    @Test
    void shouldContinueExecutingRemainingClaimedRowsWhenOneExecutionThrows() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(3);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of(
                row(1L, 11L, 1),
                row(2L, 11L, 2),
                row(3L, 11L, 3)));
        doThrow(new RuntimeException("executor failure"))
                .when(stepExecutionService)
                .executeClaimedStep(row(2L, 11L, 2));
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);
        assertDoesNotThrow(worker::pollAndProcessDueSteps);

        verify(stepExecutionService, times(3)).executeClaimedStep(any());
        verify(stepExecutionService, times(1))
                .markClaimedStepFailedAfterWorkerException(eq(row(2L, 11L, 2)), any(RuntimeException.class));
    }

    @Test
    void shouldRetryCompensationWhenFirstAttemptFailsThenSucceed() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(3);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of(
                row(1L, 11L, 1),
                row(2L, 11L, 2),
                row(3L, 11L, 3)));
        doThrow(new RuntimeException("executor failure"))
                .when(stepExecutionService)
                .executeClaimedStep(row(2L, 11L, 2));
        doThrow(new RuntimeException("transient db error"))
                .doNothing()
                .when(stepExecutionService)
                .markClaimedStepFailedAfterWorkerException(eq(row(2L, 11L, 2)), any(RuntimeException.class));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);

        assertDoesNotThrow(worker::pollAndProcessDueSteps);
        verify(stepExecutionService, times(3)).executeClaimedStep(any());
        verify(stepExecutionService, times(2))
                .markClaimedStepFailedAfterWorkerException(eq(row(2L, 11L, 2)), any(RuntimeException.class));
    }

    @Test
    void shouldContinuePollWhenCompensationFailsAllAttempts() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(3);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(50), eq(1), any())).thenReturn(List.of());
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of(
                row(1L, 11L, 1),
                row(2L, 11L, 2),
                row(3L, 11L, 3)));
        doThrow(new RuntimeException("executor failure"))
                .when(stepExecutionService)
                .executeClaimedStep(row(2L, 11L, 2));
        doThrow(new RuntimeException("db unavailable"))
                .when(stepExecutionService)
                .markClaimedStepFailedAfterWorkerException(eq(row(2L, 11L, 2)), any(RuntimeException.class));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T15:00:00Z"), ZoneOffset.UTC);
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);

        assertDoesNotThrow(worker::pollAndProcessDueSteps);
        verify(stepExecutionService, times(3)).executeClaimedStep(any());
        verify(stepExecutionService, times(3))
                .markClaimedStepFailedAfterWorkerException(eq(row(2L, 11L, 2)), any(RuntimeException.class));
    }

    @Test
    void shouldRecoverStaleProcessingBeforeClaimingDuePendingSteps() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setClaimBatchSize(5);
        properties.setMaxStepsPerPoll(5);
        properties.setStaleProcessingBatchSize(2);
        properties.setStaleProcessingTimeoutMinutes(15);
        properties.setStaleProcessingRequeueLimit(1);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.recoverStaleProcessingSteps(any(), eq(2), eq(1), any())).thenReturn(List.of(
                staleRow(10L, 110L, 1, PolicyExecutionStepStatus.PENDING, 1, PolicyExecutionStepClaimRepository.StaleRecoveryOutcome.REQUEUED)));
        when(claimRepository.claimDuePendingSteps(any(), eq(5))).thenReturn(List.of());

        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-08T12:00:00Z"), ZoneOffset.UTC);
        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, fixedClock);
        worker.pollAndProcessDueSteps();

        var inOrder = inOrder(claimRepository, stepExecutionService);
        inOrder.verify(claimRepository).recoverStaleProcessingSteps(any(), eq(2), eq(1), any());
        inOrder.verify(stepExecutionService).applyStaleProcessingRecovery(any());
        inOrder.verify(claimRepository).claimDuePendingSteps(any(), eq(5));
    }

    @Test
    void shouldSkipStaleRecoveryWhenDisabled() {
        PolicyWorkerProperties properties = new PolicyWorkerProperties();
        properties.setStaleProcessingEnabled(false);
        properties.setClaimBatchSize(3);
        properties.setMaxStepsPerPoll(3);

        PolicyExecutionStepClaimRepository claimRepository = mock(PolicyExecutionStepClaimRepository.class);
        PolicyStepExecutionService stepExecutionService = mock(PolicyStepExecutionService.class);
        when(claimRepository.claimDuePendingSteps(any(), eq(3))).thenReturn(List.of());

        PolicyExecutionDueWorker worker = new PolicyExecutionDueWorker(properties, claimRepository, stepExecutionService, Clock.systemUTC());
        worker.pollAndProcessDueSteps();

        verify(claimRepository, times(0)).recoverStaleProcessingSteps(any(), anyInt(), anyInt(), any());
        verify(stepExecutionService, times(0)).applyStaleProcessingRecovery(any());
    }

    private PolicyExecutionStepClaimRepository.StaleRecoveryRow staleRow(
            long id,
            long runId,
            int stepOrder,
            PolicyExecutionStepStatus status,
            int staleRecoveryCount,
            PolicyExecutionStepClaimRepository.StaleRecoveryOutcome outcome) {
        return new PolicyExecutionStepClaimRepository.StaleRecoveryRow(
                id,
                runId,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                stepOrder,
                status,
                staleRecoveryCount,
                outcome);
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
