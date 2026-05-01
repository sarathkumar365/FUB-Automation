package com.fuba.automation_engine.controller.dto;

import java.time.OffsetDateTime;

public record WorkflowVersionSummary(
        Integer versionNumber,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
