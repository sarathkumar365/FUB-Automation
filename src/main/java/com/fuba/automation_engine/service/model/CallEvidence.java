package com.fuba.automation_engine.service.model;

import java.time.OffsetDateTime;

// Unified call shape used by both the local-evidence path (mapped from
// ProcessedCallEntity) and the FUB-fallback path (mapped from FubCallResponseDto).
// Lets WaitAndCheckCommunicationWorkflowStep run the same classifier over both.
public record CallEvidence(
        String sourcePersonId,
        OffsetDateTime callStartedAt,
        Integer durationSeconds,
        String outcome,
        boolean isIncoming) {
}
