package com.fuba.automation_engine.persistence.entity;

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
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(
        name = "policy_execution_runs",
        indexes = {
                @Index(name = "idx_policy_execution_runs_status_created_at", columnList = "status, created_at"),
                @Index(name = "uk_policy_execution_runs_idempotency_key", columnList = "idempotency_key", unique = true)
        })
public class PolicyExecutionRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebhookSource source;

    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "webhook_event_id")
    private Long webhookEventId;

    @Column(name = "source_lead_id", length = 255)
    private String sourceLeadId;

    @Column(nullable = false, length = 64)
    private String domain;

    @Column(name = "policy_key", nullable = false, length = 128)
    private String policyKey;

    @Column(name = "policy_version", nullable = false)
    private Long policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_blueprint_snapshot", nullable = false)
    private Map<String, Object> policyBlueprintSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PolicyExecutionRunStatus status;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
