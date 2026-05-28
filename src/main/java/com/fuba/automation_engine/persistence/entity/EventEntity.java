package com.fuba.automation_engine.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted domain-event row. Backs the unified pipeline described in
 * {@code Docs/features/domain-events/plan.md}. Schema source of truth is the
 * Flyway migration {@code V22__create_events_table.sql}; the {@link Table}
 * annotation below mirrors it for Hibernate validation.
 *
 * <p><b>Naming caution — three related-but-distinct concepts live in this codebase:</b>
 * <ul>
 *   <li>{@link WebhookEventEntity} (table {@code webhook_events}) — the raw
 *       inbound webhook from FUB. Pre-existing.</li>
 *   <li>{@code EventEntity} (this class, table {@code events}) — the persisted
 *       domain event the engine emits when state actually changes or an append
 *       happens. NEW in Phase 2.</li>
 *   <li>{@link com.fuba.automation_engine.service.event.DomainEvent} — the
 *       in-memory record handed to listeners after commit. Same data as this
 *       entity but a plain value type, so listeners don't depend on JPA.</li>
 * </ul>
 * {@code sourceEventId} is the FK back to {@code WebhookEventEntity.id} — the
 * audit trail linking each domain event to the webhook that caused it (null
 * for engine-synthesized events).
 *
 * <p>Nullability mirrors V22:
 * <ul>
 *   <li>{@code sourceEventId} — nullable; engine-synthesized events have no
 *       upstream webhook.</li>
 *   <li>{@code entityType} / {@code entityId} — nullable for forward-flex
 *       (future system-level events). For every event kind Phase 2-4 emit
 *       ({@code person.*}, {@code call.*}, {@code note.*}) the application
 *       always populates both.</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "events",
        indexes = {
                @Index(name = "idx_events_kind_created_at", columnList = "event_kind, created_at"),
                @Index(name = "idx_events_entity", columnList = "entity_type, entity_id, created_at")
        })
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_kind", nullable = false, length = 64)
    private String eventKind;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "source_event_id")
    private Long sourceEventId;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
