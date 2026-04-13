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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(
        name = "workflow_run_steps",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_workflow_run_steps_run_node",
                        columnNames = {"run_id", "node_id"})
        },
        indexes = {
                @Index(name = "idx_workflow_run_steps_status_due_at", columnList = "status, due_at"),
                @Index(name = "idx_workflow_run_steps_run_id", columnList = "run_id")
        })
public class WorkflowRunStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(name = "step_type", nullable = false, length = 128)
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowRunStepStatus status;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depends_on_node_ids")
    private List<String> dependsOnNodeIds;

    @Column(name = "pending_dependency_count", nullable = false)
    private Integer pendingDependencyCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot")
    private Map<String, Object> configSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_config")
    private Map<String, Object> resolvedConfig;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs")
    private Map<String, Object> outputs;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "stale_recovery_count", nullable = false)
    private Integer staleRecoveryCount = 0;

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
        if (pendingDependencyCount == null) {
            pendingDependencyCount = 0;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (staleRecoveryCount == null) {
            staleRecoveryCount = 0;
        }
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
