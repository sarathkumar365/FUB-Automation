package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import java.time.OffsetDateTime;

/**
 * Per-stream "recent processed calls" row for a lead. Duplicates the
 * activity-timeline entry intentionally — the unified timeline is good for
 * chronology; per-stream arrays let the UI render per-tab detail without
 * re-fetching or filtering a mixed-kind array.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadRecentCallResponse(
        Long id,
        Long callId,
        ProcessedCallStatus status,
        String outcome,
        Boolean isIncoming,
        Integer durationSeconds,
        OffsetDateTime callStartedAt) {
}
