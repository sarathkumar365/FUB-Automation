package com.fuba.automation_engine.service.event;

import java.time.OffsetDateTime;
import java.util.Set;

public record EngineWriteRecord(
        long id,
        String entityType,
        String entityId,
        Set<String> changedFields,
        Long runId,
        OffsetDateTime recordedAt) {

    public EngineWriteRecord {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType must be non-blank");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must be non-blank");
        }
        if (changedFields == null || changedFields.isEmpty()) {
            throw new IllegalArgumentException("changedFields must be non-empty");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt must be non-null");
        }
        changedFields = Set.copyOf(changedFields);
    }
}
