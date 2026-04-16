package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.controller.dto.WorkflowRunDetailResponse;
import com.fuba.automation_engine.controller.dto.WorkflowRunStepDetail;
import com.fuba.automation_engine.controller.dto.WorkflowRunSummary;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.support.KeyNormalizationHelper;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final WorkflowRunRepository runRepository;
    private final WorkflowRunStepRepository runStepRepository;

    public WorkflowRunQueryService(
            WorkflowRunRepository runRepository,
            WorkflowRunStepRepository runStepRepository) {
        this.runRepository = runRepository;
        this.runStepRepository = runStepRepository;
    }

    @Transactional(readOnly = true)
    public ListRunsResult listRunsForKey(String key, String statusFilter, Integer page, Integer size) {
        String normalizedWorkflowKey = KeyNormalizationHelper.normalizeWorkflowKey(key);
        if (normalizedWorkflowKey == null) {
            return new ListRunsResult(ListRunsStatus.INVALID_INPUT, null, "workflow key is required");
        }

        Optional<WorkflowRunStatus> parsedStatusOpt = parseStatusFilter(statusFilter);
        if (parsedStatusOpt.isEmpty() && hasText(statusFilter)) {
            return new ListRunsResult(ListRunsStatus.INVALID_INPUT, null, "Invalid workflow run status filter");
        }

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<WorkflowRunEntity> runPage = runRepository.findByWorkflowKeyAndStatusFilter(
                normalizedWorkflowKey,
                parsedStatusOpt.orElse(null),
                pageable);

        PageResult<WorkflowRunSummary> result = new PageResult<>(
                runPage.getContent().stream().map(this::toSummary).toList(),
                normalizedPage,
                normalizedSize,
                runPage.getTotalElements());
        return new ListRunsResult(ListRunsStatus.SUCCESS, result, null);
    }

    @Transactional(readOnly = true)
    public ListRunsResult listRunsCrossWorkflow(String statusFilter, Integer page, Integer size) {
        Optional<WorkflowRunStatus> parsedStatusOpt = parseStatusFilter(statusFilter);
        if (parsedStatusOpt.isEmpty() && hasText(statusFilter)) {
            return new ListRunsResult(ListRunsStatus.INVALID_INPUT, null, "Invalid workflow run status filter");
        }

        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<WorkflowRunEntity> runPage = runRepository.findByStatusFilter(parsedStatusOpt.orElse(null), pageable);
        PageResult<WorkflowRunSummary> result = new PageResult<>(
                runPage.getContent().stream().map(this::toSummary).toList(),
                normalizedPage,
                normalizedSize,
                runPage.getTotalElements());
        return new ListRunsResult(ListRunsStatus.SUCCESS, result, null);
    }

    @Transactional(readOnly = true)
    public RunDetailResult getRunDetail(Long runId) {
        if (runId == null || runId <= 0) {
            return new RunDetailResult(RunDetailStatus.INVALID_INPUT, null, "runId must be positive");
        }

        Optional<WorkflowRunEntity> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) {
            return new RunDetailResult(RunDetailStatus.NOT_FOUND, null, "Workflow run not found");
        }

        WorkflowRunEntity run = runOpt.get();
        List<WorkflowRunStepDetail> steps = runStepRepository.findByRunId(runId).stream()
                .sorted(Comparator.comparing(WorkflowRunStepEntity::getId))
                .map(this::toStepDetail)
                .toList();

        WorkflowRunDetailResponse detail = new WorkflowRunDetailResponse(
                run.getId(),
                run.getWorkflowKey(),
                resolveWorkflowVersionNumber(run),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getReasonCode(),
                run.getCreatedAt(),
                toRunCompletedAt(run),
                run.getTriggerPayload(),
                run.getSourceLeadId(),
                run.getEventId(),
                steps);
        return new RunDetailResult(RunDetailStatus.SUCCESS, detail, null);
    }

    private WorkflowRunSummary toSummary(WorkflowRunEntity run) {
        return new WorkflowRunSummary(
                run.getId(),
                run.getWorkflowKey(),
                resolveWorkflowVersionNumber(run),
                run.getStatus() != null ? run.getStatus().name() : null,
                run.getReasonCode(),
                run.getCreatedAt(),
                toRunCompletedAt(run));
    }

    private Long resolveWorkflowVersionNumber(WorkflowRunEntity run) {
        // Stored value is append-only workflow version_number, not JPA optimistic-lock version.
        if (run == null || run.getWorkflowVersion() == null) {
            return 1L;
        }
        return run.getWorkflowVersion();
    }

    private WorkflowRunStepDetail toStepDetail(WorkflowRunStepEntity step) {
        return new WorkflowRunStepDetail(
                step.getId(),
                step.getNodeId(),
                step.getStepType(),
                step.getStatus() != null ? step.getStatus().name() : null,
                step.getResultCode(),
                step.getOutputs(),
                step.getErrorMessage(),
                step.getRetryCount(),
                step.getDueAt(),
                step.getCreatedAt(),
                toStepCompletedAt(step));
    }

    private OffsetDateTime toRunCompletedAt(WorkflowRunEntity run) {
        if (run.getStatus() == null || run.getStatus() == WorkflowRunStatus.PENDING) {
            return null;
        }
        return run.getUpdatedAt();
    }

    private OffsetDateTime toStepCompletedAt(WorkflowRunStepEntity step) {
        if (step.getStatus() == null) {
            return null;
        }
        if (step.getStatus() == WorkflowRunStepStatus.COMPLETED
                || step.getStatus() == WorkflowRunStepStatus.FAILED
                || step.getStatus() == WorkflowRunStepStatus.SKIPPED) {
            return step.getUpdatedAt();
        }
        return null;
    }

    private Optional<WorkflowRunStatus> parseStatusFilter(String statusFilter) {
        if (!hasText(statusFilter)) {
            return Optional.empty();
        }
        try {
            return Optional.of(WorkflowRunStatus.valueOf(statusFilter.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        return Math.max(0, page);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.max(1, size);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record PageResult<T>(List<T> items, int page, int size, long total) {
    }

    public record ListRunsResult(ListRunsStatus status, PageResult<WorkflowRunSummary> page, String errorMessage) {
    }

    public record RunDetailResult(RunDetailStatus status, WorkflowRunDetailResponse detail, String errorMessage) {
    }

    public enum ListRunsStatus {
        SUCCESS,
        INVALID_INPUT
    }

    public enum RunDetailStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_INPUT
    }
}
