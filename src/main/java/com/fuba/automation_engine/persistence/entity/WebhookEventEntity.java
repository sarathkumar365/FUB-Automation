package com.fuba.automation_engine.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_events_source_event_id", columnNames = {"source", "event_id"})
        },
        indexes = {
                @Index(name = "idx_webhook_events_status_received_at", columnList = "status, received_at"),
                @Index(name = "idx_webhook_events_source_event_type_received_at_id", columnList = "source, event_type, received_at, id"),
                @Index(name = "idx_webhook_events_status_received_at_id", columnList = "status, received_at, id"),
                @Index(name = "idx_webhook_events_received_at_id", columnList = "received_at, id")
        })
public class WebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookSource source;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
