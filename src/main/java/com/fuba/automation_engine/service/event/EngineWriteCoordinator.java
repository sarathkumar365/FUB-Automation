package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Supplier;

public interface EngineWriteCoordinator {

    <T> T applyScalarFieldUpdate(
            String sourcePersonId,
            Map<String, JsonNode> fieldUpdates,
            Long runId,
            Supplier<T> fubCall);

    <T> T applyEntityAppendTrackedOnly(
            String sourcePersonId,
            String fieldName,
            Long runId,
            Supplier<T> fubCall);

    <T> T applyEntityCreateTrackedOnly(
            String entityType,
            String sourcePersonId,
            Long runId,
            Supplier<T> fubCall,
            SideEffectRecorder<T> recordSideEffects);

    @FunctionalInterface
    interface SideEffectRecorder<T> {
        void record(EngineWriteTracker tracker, T fubResult, RecordContext context);
    }

    record RecordContext(String entityType, String sourcePersonId, Long runId) {}
}
