package com.fuba.automation_engine.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
@Table(
        name = "leads",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_leads_source_system_source_lead_id",
                        columnNames = {"source_system", "source_lead_id"})
        },
        indexes = {
                @Index(
                        name = "idx_leads_source_system_status_updated_at",
                        columnList = "source_system, status, updated_at"),
                @Index(name = "idx_leads_last_synced_at", columnList = "last_synced_at")
        })
public class LeadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    // Raw identifier assigned by the source system (e.g. FUB personId rendered as a string).
    // Combined with sourceSystem it forms the external composite key that uniquely identifies
    // this lead across ingest reruns — do NOT rewrite it to a local/surrogate value.
    @Column(name = "source_lead_id", nullable = false, length = 255)
    private String sourceLeadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lead_details", nullable = false)
    private JsonNode leadDetails;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt;
}
