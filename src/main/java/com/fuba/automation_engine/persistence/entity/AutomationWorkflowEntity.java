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
import jakarta.persistence.Version;
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
        name = "automation_workflows",
        indexes = {
                @Index(name = "idx_automation_workflows_key", columnList = "\"key\", id")
        })
public class AutomationWorkflowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "\"key\"", nullable = false, length = 128)
    private String key;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "\"trigger\"")
    private Map<String, Object> trigger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph", nullable = false)
    private Map<String, Object> graph;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkflowStatus status;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (versionNumber == null) {
            versionNumber = 1;
        }
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
