package com.flux.controller.dto;

import com.flux.persistence.entity.ProcessedCallStatus;
import java.time.OffsetDateTime;

public record ProcessedCallSummaryResponse(
        Long callId,
        ProcessedCallStatus status,
        String ruleApplied,
        Long taskId,
        String failureReason,
        Integer retryCount,
        OffsetDateTime updatedAt) {
}
