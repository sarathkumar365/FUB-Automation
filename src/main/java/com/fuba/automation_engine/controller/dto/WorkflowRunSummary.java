package com.fuba.automation_engine.controller.dto;

import java.time.OffsetDateTime;

public record WorkflowRunSummary(
        Long id,
        String workflowKey,
        Long workflowVersionNumber,
        String status,
        String reasonCode,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {
}
