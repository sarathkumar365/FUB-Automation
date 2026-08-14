package com.flux.service.workflow;

import com.flux.persistence.entity.WorkflowRunEntity;
import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.entity.WorkflowRunStepEntity;
import com.flux.persistence.entity.WorkflowRunStepStatus;
import com.flux.persistence.repository.WorkflowRunRepository;
import com.flux.persistence.repository.WorkflowRunStepRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunControlService {
    // Intentionally separate from WorkflowRunQueryService: this service owns mutating
    // operator-control commands (cancel), while query service remains read-only/list/detail.

    static final String CANCELED_BY_OPERATOR = "CANCELED_BY_OPERATOR";
    public static final String SUPERSEDED_BY_NEWER_EVENT = "SUPERSEDED_BY_NEWER_EVENT";

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

        finalizeCanceled(run, CANCELED_BY_OPERATOR, null);
        return new CancelRunResult(CancelRunStatus.SUCCESS, run.getId(), null);
    }

    /**
     * Phase 5 supersede: a newer triggering event for the same (workflow, person)
     * cancels the in-flight run. Distinct from operator cancel — it attributes the
     * triggering {@code domainEventId} and uses {@code SUPERSEDED_BY_NEWER_EVENT}.
     * No-op if the run is already terminal (only {@code PENDING} runs are active).
     */
    @Transactional
    public void supersede(Long runId, Long domainEventId) {
        WorkflowRunEntity run = runRepository.findById(runId).orElse(null);
        if (run == null || run.getStatus() != WorkflowRunStatus.PENDING) {
            return;
        }
        finalizeCanceled(run, SUPERSEDED_BY_NEWER_EVENT, domainEventId);
    }

    private void finalizeCanceled(WorkflowRunEntity run, String reasonCode, Long domainEventId) {
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
        run.setReasonCode(reasonCode);
        if (domainEventId != null) {
            run.setDomainEventId(domainEventId);
        }
        runRepository.save(run);
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
