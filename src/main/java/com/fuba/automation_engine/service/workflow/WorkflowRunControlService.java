package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunControlService {
    // Intentionally separate from WorkflowRunQueryService: this service owns mutating
    // operator-control commands (cancel), while query service remains read-only/list/detail.

    static final String CANCELED_BY_OPERATOR = "CANCELED_BY_OPERATOR";

    private final WorkflowRunRepository runRepository;
    private final WorkflowRunStepRepository runStepRepository;

    public WorkflowRunControlService(
            WorkflowRunRepository runRepository,
            WorkflowRunStepRepository runStepRepository) {
        this.runRepository = runRepository;
        this.runStepRepository = runStepRepository;
    }

    @Transactional
    public CancelRunResult cancelRun(Long runId) {
        if (runId == null || runId <= 0) {
            return new CancelRunResult(CancelRunStatus.INVALID_INPUT, null, "runId must be positive");
        }

        Optional<WorkflowRunEntity> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) {
            return new CancelRunResult(CancelRunStatus.NOT_FOUND, null, "Workflow run not found");
        }

        WorkflowRunEntity run = runOpt.get();
        WorkflowRunStatus currentStatus = run.getStatus();
        if (currentStatus == WorkflowRunStatus.CANCELED) {
            return new CancelRunResult(CancelRunStatus.SUCCESS, run.getId(), null);
        }
        if (currentStatus != WorkflowRunStatus.PENDING) {
            return new CancelRunResult(
                    CancelRunStatus.CONFLICT,
                    null,
                    "Workflow run cannot be canceled from status " + currentStatus);
        }

        List<WorkflowRunStepEntity> steps = runStepRepository.findByRunId(run.getId());
        for (WorkflowRunStepEntity step : steps) {
            if (step.getStatus() == WorkflowRunStepStatus.PENDING
                    || step.getStatus() == WorkflowRunStepStatus.WAITING_DEPENDENCY) {
                step.setStatus(WorkflowRunStepStatus.SKIPPED);
                step.setDueAt(null);
                runStepRepository.save(step);
            }
        }

        run.setStatus(WorkflowRunStatus.CANCELED);
        run.setReasonCode(CANCELED_BY_OPERATOR);
        runRepository.save(run);

        return new CancelRunResult(CancelRunStatus.SUCCESS, run.getId(), null);
    }

    public record CancelRunResult(CancelRunStatus status, Long runId, String errorMessage) {
    }

    public enum CancelRunStatus {
        SUCCESS,
        INVALID_INPUT,
        NOT_FOUND,
        CONFLICT
    }
}
