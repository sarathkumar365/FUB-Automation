package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import java.time.OffsetDateTime;

/**
 * One row in the leads list. The {@code snapshot} field projects selected
 * fields from {@code leads.lead_details} (name, firstName, lastName, stage,
 * assignedUserId, assignedTo, tags, phones, emails, etc.) — the UI reads
 * these defensively because the FUB person payload shape is not enforced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadFeedItemResponse(
        Long id,
        String sourceSystem,
        String sourceLeadId,
        LeadStatus status,
        JsonNode snapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSyncedAt) {
}
