package com.fuba.automation_engine.service.event;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

public interface EngineWriteTracker {

    EngineWriteRecord record(String entityType, String entityId, Set<String> changedFields, Long runId);

    Optional<EngineWriteRecord> findMatching(
            String entityType, String entityId, Set<String> diffFields, OffsetDateTime now);

    void evictExpired(OffsetDateTime now);
}
