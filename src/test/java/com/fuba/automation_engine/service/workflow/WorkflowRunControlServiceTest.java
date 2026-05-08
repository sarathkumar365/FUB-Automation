package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService.CancelRunResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService.CancelRunStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRunControlServiceTest {

    @Mock
    private WorkflowRunRepository runRepository;

    @Mock
    private WorkflowRunStepRepository runStepRepository;

    @InjectMocks
    private WorkflowRunControlService service;

    @Test
    void cancelRunShouldReturnInvalidInputWhenRunIdNotPositive() {
        CancelRunResult result = service.cancelRun(0L);

        assertEquals(CancelRunStatus.INVALID_INPUT, result.status());
        verify(runRepository, never()).findById(any());
    }

    @Test
    void cancelRunShouldReturnNotFoundWhenRunMissing() {
        when(runRepository.findById(11L)).thenReturn(Optional.empty());

        CancelRunResult result = service.cancelRun(11L);

        assertEquals(CancelRunStatus.NOT_FOUND, result.status());
    }

    @Test
    void cancelRunShouldBeIdempotentWhenAlreadyCanceled() {
        WorkflowRunEntity run = run(77L, WorkflowRunStatus.CANCELED);
        when(runRepository.findById(77L)).thenReturn(Optional.of(run));

        CancelRunResult result = service.cancelRun(77L);

        assertEquals(CancelRunStatus.SUCCESS, result.status());
        assertEquals(77L, result.runId());
        verify(runStepRepository, never()).findByRunId(any());
    }

    @Test
    void cancelRunShouldReturnConflictWhenRunIsTerminal() {
        WorkflowRunEntity run = run(20L, WorkflowRunStatus.COMPLETED);
        when(runRepository.findById(20L)).thenReturn(Optional.of(run));

        CancelRunResult result = service.cancelRun(20L);

        assertEquals(CancelRunStatus.CONFLICT, result.status());
        assertNull(result.runId());
        verify(runStepRepository, never()).findByRunId(any());
    }

    @Test
    void cancelRunShouldCancelPendingRunAndSkipPendingAndWaitingSteps() {
        WorkflowRunEntity run = run(40L, WorkflowRunStatus.PENDING);
        WorkflowRunStepEntity pending = step(1L, WorkflowRunStepStatus.PENDING);
        WorkflowRunStepEntity waiting = step(2L, WorkflowRunStepStatus.WAITING_DEPENDENCY);
        WorkflowRunStepEntity processing = step(3L, WorkflowRunStepStatus.PROCESSING);

        when(runRepository.findById(40L)).thenReturn(Optional.of(run));
        when(runStepRepository.findByRunId(40L)).thenReturn(List.of(pending, waiting, processing));

        CancelRunResult result = service.cancelRun(40L);

        assertEquals(CancelRunStatus.SUCCESS, result.status());
        assertEquals(WorkflowRunStatus.CANCELED, run.getStatus());
        assertEquals(WorkflowRunControlService.CANCELED_BY_OPERATOR, run.getReasonCode());

        assertEquals(WorkflowRunStepStatus.SKIPPED, pending.getStatus());
        assertEquals(WorkflowRunStepStatus.SKIPPED, waiting.getStatus());
        assertEquals(WorkflowRunStepStatus.PROCESSING, processing.getStatus());

        verify(runStepRepository).save(pending);
        verify(runStepRepository).save(waiting);
        verify(runStepRepository, never()).save(processing);
        verify(runRepository).save(run);
    }

    private WorkflowRunEntity run(Long id, WorkflowRunStatus status) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(id);
        run.setStatus(status);
        return run;
    }

    private WorkflowRunStepEntity step(Long id, WorkflowRunStepStatus status) {
        WorkflowRunStepEntity step = new WorkflowRunStepEntity();
        step.setId(id);
        step.setStatus(status);
        return step;
    }
}
