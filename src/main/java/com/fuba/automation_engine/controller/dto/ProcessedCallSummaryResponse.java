package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
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
