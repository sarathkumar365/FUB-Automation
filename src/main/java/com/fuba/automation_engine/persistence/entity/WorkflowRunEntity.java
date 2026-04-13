package com.fuba.automation_engine.persistence.entity;

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
        name = "workflow_runs",
        indexes = {
                @Index(name = "idx_workflow_runs_status_created_at", columnList = "status, created_at"),
                @Index(name = "uk_workflow_runs_idempotency_key", columnList = "idempotency_key", unique = true),
                @Index(name = "idx_workflow_runs_workflow_id", columnList = "workflow_id, created_at")
        })
public class WorkflowRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "workflow_key", nullable = false, length = 128)
    private String workflowKey;

    @Column(name = "workflow_version", nullable = false)
    private Long workflowVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_graph_snapshot", nullable = false)
    private Map<String, Object> workflowGraphSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_payload")
    private Map<String, Object> triggerPayload;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "webhook_event_id")
    private Long webhookEventId;

    @Column(name = "source_lead_id", length = 255)
    private String sourceLeadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowRunStatus status;

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
