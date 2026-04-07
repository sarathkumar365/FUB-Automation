package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import java.time.OffsetDateTime;
import java.util.List;

public interface PolicyExecutionStepClaimRepository {

    List<ClaimedStepRow> claimDuePendingSteps(OffsetDateTime now, int limit);

    record ClaimedStepRow(
            long id,
            long runId,
            PolicyStepType stepType,
            int stepOrder,
            OffsetDateTime dueAt,
            PolicyExecutionStepStatus status) {
    }
}
