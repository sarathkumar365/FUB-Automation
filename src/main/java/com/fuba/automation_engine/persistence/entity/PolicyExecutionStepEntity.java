package com.fuba.automation_engine.persistence.entity;

import com.fuba.automation_engine.service.policy.PolicyStepType;
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

@Getter
@Setter
@Entity
@Table(
        name = "policy_execution_steps",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_policy_execution_steps_run_step_order",
                        columnNames = {"run_id", "step_order"})
        },
        indexes = {
                @Index(name = "idx_policy_execution_steps_status_due_at", columnList = "status, due_at"),
                @Index(name = "idx_policy_execution_steps_run_id_step_order", columnList = "run_id, step_order")
        })
public class PolicyExecutionStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 64)
    private PolicyStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PolicyExecutionStepStatus status;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "depends_on_step_order")
    private Integer dependsOnStepOrder;

    @Column(name = "result_code", length = 64)
    private String resultCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

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
