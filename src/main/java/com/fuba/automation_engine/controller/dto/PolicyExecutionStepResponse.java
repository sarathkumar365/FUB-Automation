package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import java.time.OffsetDateTime;

public record PolicyExecutionStepResponse(
        Long id,
        Integer stepOrder,
        PolicyStepType stepType,
        PolicyExecutionStepStatus status,
        OffsetDateTime dueAt,
        Integer dependsOnStepOrder,
        String resultCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
