package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;

public record PolicyExecutionPlanningResult(
        PolicyExecutionRunStatus status,
        Long runId,
        String reasonCode) {
}
