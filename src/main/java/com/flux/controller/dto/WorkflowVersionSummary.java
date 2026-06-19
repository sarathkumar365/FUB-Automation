package com.flux.controller.dto;

import java.time.OffsetDateTime;

public record WorkflowVersionSummary(
        Integer versionNumber,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
