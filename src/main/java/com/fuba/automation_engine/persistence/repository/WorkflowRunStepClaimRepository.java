package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import java.time.OffsetDateTime;
import java.util.List;

public interface WorkflowRunStepClaimRepository {

    List<ClaimedStepRow> claimDuePendingSteps(OffsetDateTime now, int limit);

    List<StaleRecoveryRow> recoverStaleProcessingSteps(
            OffsetDateTime staleBefore,
            int limit,
            int requeueLimit,
            OffsetDateTime now);

    record ClaimedStepRow(
            long id,
            long runId,
            String nodeId,
            String stepType,
            OffsetDateTime dueAt,
            WorkflowRunStepStatus status) {
    }

    record StaleRecoveryRow(
            long id,
            long runId,
            String nodeId,
            String stepType,
            WorkflowRunStepStatus status,
            int staleRecoveryCount,
            StaleRecoveryOutcome outcome) {
    }

    enum StaleRecoveryOutcome {
        REQUEUED,
        FAILED
    }
}
