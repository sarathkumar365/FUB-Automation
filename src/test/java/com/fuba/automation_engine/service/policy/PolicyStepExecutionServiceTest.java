package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyStepExecutionServiceTest {

    private PolicyExecutionRunRepository runRepository;
    private PolicyExecutionStepRepository stepRepository;
    private Clock clock;

    @BeforeEach
    void setUp() {
        runRepository = mock(PolicyExecutionRunRepository.class);
        stepRepository = mock(PolicyExecutionStepRepository.class);
        clock = Clock.fixed(Instant.parse("2026-04-08T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void shouldExecuteMatchingExecutorAndCompleteStep() {
        PolicyStepExecutor executor = mock(PolicyStepExecutor.class);
        when(executor.supports(PolicyStepType.WAIT_AND_CHECK_CLAIM)).thenReturn(true);
        when(executor.execute(any())).thenReturn(PolicyStepExecutionResult.success(PolicyStepResultCode.CLAIMED));

        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(executor), clock);

        PolicyExecutionStepEntity step = stepEntity(1L, 10L, 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionStepEntity nextStep =
                stepEntity(2L, 10L, 2, PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyExecutionStepStatus.WAITING_DEPENDENCY);
        PolicyExecutionRunEntity run = runEntity(10L, "798");
        when(stepRepository.findById(1L)).thenReturn(Optional.of(step));
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        when(stepRepository.findByRunIdOrderByStepOrderAsc(10L)).thenReturn(List.of(step, nextStep));

        service.executeClaimedStep(claimedRow(1L, 10L, PolicyStepType.WAIT_AND_CHECK_CLAIM));

        assertEquals(PolicyExecutionStepStatus.COMPLETED, step.getStatus());
        assertEquals(PolicyStepResultCode.CLAIMED.name(), step.getResultCode());
        assertEquals(PolicyExecutionStepStatus.PENDING, nextStep.getStatus());
        assertEquals(OffsetDateTime.parse("2026-04-08T12:10:00Z"), nextStep.getDueAt());
        assertEquals(PolicyExecutionRunStatus.PENDING, run.getStatus());
        verify(stepRepository).save(step);
        verify(stepRepository).save(nextStep);
        verify(runRepository, never()).save(any());
    }

    @Test
    void shouldMarkStepAndRunFailedWhenExecutorMissing() {
        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(), clock);

        PolicyExecutionStepEntity step =
                stepEntity(2L, 20L, 2, PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionRunEntity run = runEntity(20L, "799");
        when(stepRepository.findById(2L)).thenReturn(Optional.of(step));
        when(runRepository.findById(20L)).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(2L, 20L, PolicyStepType.WAIT_AND_CHECK_COMMUNICATION));

        assertEquals(PolicyExecutionStepStatus.FAILED, step.getStatus());
        assertEquals(PolicyExecutionRunStatus.FAILED, run.getStatus());
        assertEquals(PolicyStepExecutionService.EXECUTOR_NOT_FOUND, run.getReasonCode());
        verify(stepRepository).save(step);
        verify(runRepository).save(run);
    }

    @Test
    void shouldMarkStepAndRunFailedWhenExecutorThrows() {
        PolicyStepExecutor executor = mock(PolicyStepExecutor.class);
        when(executor.supports(PolicyStepType.WAIT_AND_CHECK_CLAIM)).thenReturn(true);
        when(executor.execute(any())).thenThrow(new RuntimeException("boom"));

        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(executor), clock);

        PolicyExecutionStepEntity step = stepEntity(3L, 30L, 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionRunEntity run = runEntity(30L, "800");
        when(stepRepository.findById(3L)).thenReturn(Optional.of(step));
        when(runRepository.findById(30L)).thenReturn(Optional.of(run));

        service.executeClaimedStep(claimedRow(3L, 30L, PolicyStepType.WAIT_AND_CHECK_CLAIM));

        assertEquals(PolicyExecutionStepStatus.FAILED, step.getStatus());
        assertEquals(PolicyExecutionRunStatus.FAILED, run.getStatus());
        assertEquals(PolicyStepExecutionService.EXECUTION_EXCEPTION, run.getReasonCode());
    }

    @Test
    void shouldNoopWhenClaimedStepNoLongerExists() {
        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(), clock);
        when(stepRepository.findById(99L)).thenReturn(Optional.empty());

        service.executeClaimedStep(claimedRow(99L, 50L, PolicyStepType.WAIT_AND_CHECK_CLAIM));

        verify(runRepository, never()).findById(any());
        verify(stepRepository, never()).save(any());
    }

    @Test
    void shouldTerminalizeRunAndSkipDownstreamStepsWhenClaimNotClaimed() {
        PolicyStepExecutor executor = mock(PolicyStepExecutor.class);
        when(executor.supports(PolicyStepType.WAIT_AND_CHECK_CLAIM)).thenReturn(true);
        when(executor.execute(any())).thenReturn(PolicyStepExecutionResult.success(PolicyStepResultCode.NOT_CLAIMED));

        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(executor), clock);

        PolicyExecutionStepEntity claimStep = stepEntity(
                10L, 88L, 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionStepEntity communicationStep = stepEntity(
                11L, 88L, 2, PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyExecutionStepStatus.WAITING_DEPENDENCY);
        communicationStep.setDueAt(OffsetDateTime.parse("2026-04-08T12:30:00Z"));
        PolicyExecutionStepEntity actionStep = stepEntity(
                12L, 88L, 3, PolicyStepType.ON_FAILURE_EXECUTE_ACTION, PolicyExecutionStepStatus.WAITING_DEPENDENCY);
        actionStep.setDueAt(OffsetDateTime.parse("2026-04-08T12:40:00Z"));

        PolicyExecutionRunEntity run = runEntity(88L, "900");
        when(stepRepository.findById(10L)).thenReturn(Optional.of(claimStep));
        when(runRepository.findById(88L)).thenReturn(Optional.of(run));
        when(stepRepository.findByRunIdOrderByStepOrderAsc(88L))
                .thenReturn(List.of(claimStep, communicationStep, actionStep));

        service.executeClaimedStep(claimedRow(10L, 88L, PolicyStepType.WAIT_AND_CHECK_CLAIM));

        assertEquals(PolicyExecutionStepStatus.COMPLETED, claimStep.getStatus());
        assertEquals(PolicyStepResultCode.NOT_CLAIMED.name(), claimStep.getResultCode());
        assertEquals(PolicyExecutionStepStatus.SKIPPED, communicationStep.getStatus());
        assertEquals(null, communicationStep.getDueAt());
        assertEquals(PolicyExecutionStepStatus.SKIPPED, actionStep.getStatus());
        assertEquals(null, actionStep.getDueAt());
        assertEquals(PolicyExecutionRunStatus.COMPLETED, run.getStatus());
        assertEquals(PolicyTerminalOutcome.NON_ESCALATED_CLOSED.name(), run.getReasonCode());
    }

    @Test
    void shouldFailRunWhenTransitionNextStepIsMissing() {
        PolicyStepExecutor executor = mock(PolicyStepExecutor.class);
        when(executor.supports(PolicyStepType.WAIT_AND_CHECK_CLAIM)).thenReturn(true);
        when(executor.execute(any())).thenReturn(PolicyStepExecutionResult.success(PolicyStepResultCode.CLAIMED));

        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(executor), clock);

        PolicyExecutionStepEntity claimStep = stepEntity(
                40L, 120L, 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionRunEntity run = runEntity(120L, "901");
        when(stepRepository.findById(40L)).thenReturn(Optional.of(claimStep));
        when(runRepository.findById(120L)).thenReturn(Optional.of(run));
        when(stepRepository.findByRunIdOrderByStepOrderAsc(120L)).thenReturn(List.of(claimStep));

        service.executeClaimedStep(claimedRow(40L, 120L, PolicyStepType.WAIT_AND_CHECK_CLAIM));

        assertEquals(PolicyExecutionStepStatus.FAILED, claimStep.getStatus());
        assertEquals(PolicyExecutionRunStatus.FAILED, run.getStatus());
        assertEquals(PolicyStepExecutionService.TRANSITION_TARGET_NOT_FOUND, run.getReasonCode());
    }

    @Test
    void shouldMarkStepAndRunFailedWhenWorkerCatchCompensates() {
        PolicyStepExecutionService service =
                new PolicyStepExecutionService(runRepository, stepRepository, List.of(), clock);

        PolicyExecutionStepEntity step = stepEntity(
                77L, 177L, 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PROCESSING);
        PolicyExecutionRunEntity run = runEntity(177L, "950");
        when(stepRepository.findById(77L)).thenReturn(Optional.of(step));
        when(runRepository.findById(177L)).thenReturn(Optional.of(run));

        RuntimeException thrown = new RuntimeException("db write failed");
        service.markClaimedStepFailedAfterWorkerException(
                claimedRow(77L, 177L, PolicyStepType.WAIT_AND_CHECK_CLAIM),
                thrown);

        assertEquals(PolicyExecutionStepStatus.FAILED, step.getStatus());
        assertEquals(PolicyExecutionRunStatus.FAILED, run.getStatus());
        assertEquals(PolicyStepExecutionService.WORKER_UNHANDLED_EXCEPTION, run.getReasonCode());
        assertTrue(step.getErrorMessage().contains("RuntimeException"));
        verify(stepRepository).save(step);
        verify(runRepository).save(run);
    }

    private PolicyExecutionStepClaimRepository.ClaimedStepRow claimedRow(long stepId, long runId, PolicyStepType stepType) {
        return new PolicyExecutionStepClaimRepository.ClaimedStepRow(
                stepId,
                runId,
                stepType,
                1,
                null,
                PolicyExecutionStepStatus.PROCESSING);
    }

    private PolicyExecutionStepEntity stepEntity(
            long id,
            long runId,
            int stepOrder,
            PolicyStepType stepType,
            PolicyExecutionStepStatus status) {
        PolicyExecutionStepEntity step = new PolicyExecutionStepEntity();
        step.setId(id);
        step.setRunId(runId);
        step.setStepOrder(stepOrder);
        step.setStepType(stepType);
        step.setStatus(status);
        return step;
    }

    private PolicyExecutionRunEntity runEntity(long id, String sourceLeadId) {
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setId(id);
        run.setSource(WebhookSource.FUB);
        run.setSourceLeadId(sourceLeadId);
        run.setStatus(PolicyExecutionRunStatus.PENDING);
        run.setPolicyBlueprintSnapshot(Map.of(
                "steps", List.of(
                        Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", 5),
                        Map.of("type", "WAIT_AND_CHECK_COMMUNICATION", "delayMinutes", 10),
                        Map.of("type", "ON_FAILURE_EXECUTE_ACTION", "delayMinutes", 0))));
        return run;
    }
}
