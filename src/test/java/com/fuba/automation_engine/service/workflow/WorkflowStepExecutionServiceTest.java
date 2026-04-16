package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowStepExecutionServiceTest {

    @Mock
    private WorkflowRunRepository runRepository;

    @Mock
    private WorkflowRunStepRepository stepRepository;

    @Mock
    private WorkflowStepRegistry stepRegistry;

    @Mock
    private ExpressionEvaluator expressionEvaluator;

    private WorkflowStepExecutionService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowStepExecutionService(
                runRepository,
                stepRepository,
                stepRegistry,
                expressionEvaluator,
                Clock.fixed(Instant.parse("2026-04-15T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void executeClaimedStepShouldSkipWhenRunIsCanceled() {
        WorkflowRunStepEntity step = new WorkflowRunStepEntity();
        step.setId(101L);
        step.setRunId(11L);
        step.setNodeId("n1");
        step.setStatus(WorkflowRunStepStatus.PROCESSING);

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(11L);
        run.setStatus(WorkflowRunStatus.CANCELED);

        when(stepRepository.findById(101L)).thenReturn(Optional.of(step));
        when(runRepository.findById(11L)).thenReturn(Optional.of(run));

        WorkflowRunStepClaimRepository.ClaimedStepRow claimed = new WorkflowRunStepClaimRepository.ClaimedStepRow(
                101L, 11L, "n1", "delay", null, WorkflowRunStepStatus.PROCESSING);

        service.executeClaimedStep(claimed);

        assertEquals(WorkflowRunStepStatus.SKIPPED, step.getStatus());
        verify(stepRepository).save(step);
        verify(stepRegistry, never()).get(any());
    }

    @Test
    void markClaimedStepFailedAfterWorkerExceptionShouldNotOverwriteCanceledRun() {
        WorkflowRunStepEntity step = new WorkflowRunStepEntity();
        step.setId(202L);
        step.setRunId(22L);
        step.setStatus(WorkflowRunStepStatus.PROCESSING);

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(22L);
        run.setStatus(WorkflowRunStatus.CANCELED);

        when(stepRepository.findById(202L)).thenReturn(Optional.of(step));
        when(runRepository.findById(22L)).thenReturn(Optional.of(run));

        WorkflowRunStepClaimRepository.ClaimedStepRow claimed = new WorkflowRunStepClaimRepository.ClaimedStepRow(
                202L, 22L, "n2", "delay", null, WorkflowRunStepStatus.PROCESSING);

        service.markClaimedStepFailedAfterWorkerException(claimed, new RuntimeException("boom"));

        assertEquals(WorkflowRunStatus.CANCELED, run.getStatus());
        verify(runRepository, never()).save(run);
        verify(stepRepository).save(step);
    }

    @Test
    void applyStaleProcessingRecoveryShouldNotFailCanceledRun() {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(33L);
        run.setStatus(WorkflowRunStatus.CANCELED);

        WorkflowRunStepClaimRepository.StaleRecoveryRow staleRow = new WorkflowRunStepClaimRepository.StaleRecoveryRow(
                301L,
                33L,
                "n3",
                "delay",
                WorkflowRunStepStatus.FAILED,
                1,
                WorkflowRunStepClaimRepository.StaleRecoveryOutcome.FAILED);

        when(runRepository.findById(33L)).thenReturn(Optional.of(run));

        service.applyStaleProcessingRecovery(List.of(staleRow));

        assertEquals(WorkflowRunStatus.CANCELED, run.getStatus());
        verify(runRepository, never()).save(run);
    }
}
