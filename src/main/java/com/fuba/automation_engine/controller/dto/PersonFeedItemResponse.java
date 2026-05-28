package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import java.time.OffsetDateTime;

/**
 * One row in the persons list. The {@code snapshot} field projects selected
 * fields from {@code persons.person_details} (name, firstName, lastName, stage,
 * assignedUserId, assignedTo, tags, phones, emails, etc.) — the UI reads
 * these defensively because the FUB person payload shape is not enforced.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonFeedItemResponse(
        Long id,
        String sourceSystem,
        String sourcePersonId,
        PersonStatus status,
        JsonNode snapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSyncedAt) {
}
