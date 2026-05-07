package com.fuba.automation_engine.service.webhook.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizedWebhookEventContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldExposeAllRequiredAndOptionalFields() {
        ObjectNode providerMeta = OBJECT_MAPPER.createObjectNode();
        providerMeta.put("uri", "/v1/people/123");
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "peopleCreated");

        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-04-01T10:00:00Z");
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-04-01T10:00:02Z");

        NormalizedWebhookEvent event = new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-1",
                "peopleCreated",
                occurredAt,
                "123",
                NormalizedDomain.LEAD,
                NormalizedAction.CREATED,
                providerMeta,
                WebhookEventStatus.RECEIVED,
                payload,
                receivedAt,
                "hash-1");

        assertEquals(WebhookSource.FUB, event.sourceSystem());
        assertEquals("evt-1", event.eventId());
        assertEquals("peopleCreated", event.sourceEventType());
        assertEquals(occurredAt, event.occurredAt());
        assertEquals("123", event.sourceLeadId());
        assertEquals(NormalizedDomain.LEAD, event.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, event.normalizedAction());
        assertEquals("/v1/people/123", event.providerMeta().get("uri").asText());
        assertEquals(WebhookEventStatus.RECEIVED, event.status());
        assertEquals("peopleCreated", event.payload().get("eventType").asText());
        assertEquals(receivedAt, event.receivedAt());
        assertEquals("hash-1", event.payloadHash());
    }

    @Test
    void shouldAllowNullableOptionalFields() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "callsCreated");
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-04-01T10:00:02Z");

        NormalizedWebhookEvent event = new NormalizedWebhookEvent(
                WebhookSource.FUB,
                null,
                "callsCreated",
                null,
                null,
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                receivedAt,
                null);

        assertNull(event.eventId());
        assertNull(event.occurredAt());
        assertNull(event.sourceLeadId());
        assertNull(event.providerMeta());
        assertNull(event.payloadHash());
    }

    /**
     * Phase 0 (agent-followup-enforcement) renamed the misleading
     * {@code NormalizedDomain.ASSIGNMENT} to CRM-agnostic {@code LEAD}.
     * Lock that in: the old name must stay gone so a future re-introduction
     * fails loudly here in addition to compile-time, and so the V18 migration's
     * assumed source value can never legally re-appear.
     */
    @Test
    void normalizedDomainShouldHaveLeadAndNotAssignment() {
        assertTrue(
                Arrays.stream(NormalizedDomain.values())
                        .anyMatch(d -> d == NormalizedDomain.LEAD),
                "NormalizedDomain.LEAD must be present (CRM-agnostic name for lead/contact events)");
        assertFalse(
                Arrays.stream(NormalizedDomain.values())
                        .anyMatch(d -> d.name().equals("ASSIGNMENT")),
                "NormalizedDomain.ASSIGNMENT was renamed to LEAD in Phase 0; do not re-add");
    }

    /**
     * {@code NormalizedAction.ASSIGNED} was always a phantom — declared but never
     * produced by any parser. Phase 0 dropped it. Lock it gone.
     */
    @Test
    void normalizedActionShouldNotHaveAssigned() {
        assertFalse(
                Arrays.stream(NormalizedAction.values())
                        .anyMatch(a -> a.name().equals("ASSIGNED")),
                "NormalizedAction.ASSIGNED was a phantom value, removed in Phase 0; do not re-add");
    }
}
