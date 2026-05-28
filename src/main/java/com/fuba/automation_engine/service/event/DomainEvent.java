package com.fuba.automation_engine.service.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * In-memory shape of a domain event as it travels from
 * {@link DomainEventEmitter} to {@link DomainEventDispatcher}.
 *
 * <p>Distinct from
 * {@link com.fuba.automation_engine.persistence.entity.EventEntity}: the entity
 * is the persisted row (with database id, created_at); this record is the
 * dispatch-time value passed to listeners. The emitter constructs both — entity
 * for the INSERT, record for the after-commit dispatch — from the same caller
 * inputs.
 *
 * <p>Field nullability mirrors the {@code events} table per V22:
 * {@code sourceEventId}, {@code entityType}, {@code entityId} may be null;
 * {@code eventKind}, {@code sourceSystem}, {@code payload} must be set.
 * Validation is the emitter's responsibility, not the record's — see
 * {@link DomainEventEmitter}.
 */
public record DomainEvent(
        String eventKind,
        String sourceSystem,
        Long sourceEventId,
        String entityType,
        String entityId,
        JsonNode payload) {
}
