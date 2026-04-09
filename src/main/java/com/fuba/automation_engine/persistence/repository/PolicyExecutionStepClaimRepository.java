package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import java.time.OffsetDateTime;
import java.util.List;

public interface PolicyExecutionStepClaimRepository {

    List<ClaimedStepRow> claimDuePendingSteps(OffsetDateTime now, int limit);

    List<StaleRecoveryRow> recoverStaleProcessingSteps(
            OffsetDateTime staleBefore,
            int limit,
            int requeueLimit,
            OffsetDateTime now);

    record ClaimedStepRow(
            long id,
            long runId,
            PolicyStepType stepType,
            int stepOrder,
            OffsetDateTime dueAt,
            PolicyExecutionStepStatus status) {
    }

    record StaleRecoveryRow(
            long id,
            long runId,
            PolicyStepType stepType,
            int stepOrder,
            PolicyExecutionStepStatus status,
            int staleRecoveryCount,
            StaleRecoveryOutcome outcome) {
    }

    enum StaleRecoveryOutcome {
        REQUEUED,
        FAILED
    }
}
