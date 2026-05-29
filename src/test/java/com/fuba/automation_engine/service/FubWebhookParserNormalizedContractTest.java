package com.fuba.automation_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.parse.FubWebhookParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookParserNormalizedContractTest {

    private final FubWebhookParser parser = new FubWebhookParser(new ObjectMapper());

    @Test
    void shouldMapCallsCreatedToTopLevelContractAndKeepLegacyPayloadKeys() {
        String rawBody = """
                {
                  "eventId": "evt-calls-1",
                  "event": "callsCreated",
                  "resourceIds": [111, 222],
                  "uri": "/v1/calls/111"
                }
                """;

        NormalizedWebhookEvent event = parser.parse(rawBody, Map.of(
                "FUB-Signature", "sig",
                "User-Agent", "fub-test",
                "Content-Type", "application/json"));

        assertEquals("evt-calls-1", event.eventId());
        assertEquals("callsCreated", event.sourceEventType());
        assertEquals(NormalizedDomain.CALL, event.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, event.normalizedAction());
        assertNull(event.sourcePersonId());
        assertNotNull(event.providerMeta());
        assertEquals(2, event.providerMeta().get("resourceIds").size());
        assertEquals("/v1/calls/111", event.providerMeta().get("uri").asText());
        assertEquals("sig", event.providerMeta().get("headers").get("FUB-Signature").asText());

        assertEquals("callsCreated", event.payload().get("eventType").asText());
        assertEquals(2, event.payload().get("resourceIds").size());
        assertEquals("/v1/calls/111", event.payload().get("uri").asText());
        assertEquals("sig", event.payload().get("headers").get("FUB-Signature").asText());
        assertEquals(rawBody, event.payload().get("rawBody").asText());
    }

    @Test
    void shouldMapPeopleEventsAndUnknownEvents() {
        String peopleCreatedRawBody = """
                {
                  "eventId": "evt-people-created-1",
                  "event": "peopleCreated",
                  "resourceIds": [333]
                }
                """;
        String peopleUpdatedRawBody = """
                {
                  "eventId": "evt-people-updated-1",
                  "event": "peopleUpdated",
                  "resourceIds": [444]
                }
                """;
        String unknownRawBody = """
                {
                  "eventId": "evt-unknown-1",
                  "event": "somethingElse",
                  "resourceIds": [555]
                }
                """;

        NormalizedWebhookEvent peopleCreated = parser.parse(peopleCreatedRawBody, Map.of());
        NormalizedWebhookEvent peopleUpdated = parser.parse(peopleUpdatedRawBody, Map.of());
        NormalizedWebhookEvent unknown = parser.parse(unknownRawBody, Map.of());

        assertEquals(NormalizedDomain.PERSON, peopleCreated.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, peopleCreated.normalizedAction());

        assertEquals(NormalizedDomain.PERSON, peopleUpdated.normalizedDomain());
        assertEquals(NormalizedAction.UPDATED, peopleUpdated.normalizedAction());

        assertEquals(NormalizedDomain.UNKNOWN, unknown.normalizedDomain());
        assertEquals(NormalizedAction.UNKNOWN, unknown.normalizedAction());
    }

    @Test
    void shouldPopulateSourcePersonIdForPeopleEvents() {
        NormalizedWebhookEvent peopleCreated = parser.parse(
                """
                {
                  "eventId": "evt-people-created-slid",
                  "event": "peopleCreated",
                  "resourceIds": [19355]
                }
                """,
                Map.of());
        NormalizedWebhookEvent peopleUpdated = parser.parse(
                """
                {
                  "eventId": "evt-people-updated-slid",
                  "event": "peopleUpdated",
                  "resourceIds": [19355]
                }
                """,
                Map.of());
        NormalizedWebhookEvent callsCreated = parser.parse(
                """
                {
                  "eventId": "evt-calls-slid",
                  "event": "callsCreated",
                  "resourceIds": [999]
                }
                """,
                Map.of());

        assertEquals("19355", peopleCreated.sourcePersonId());
        assertEquals("19355", peopleUpdated.sourcePersonId());
        assertNull(callsCreated.sourcePersonId());
    }

    @Test
    void notesCreatedParsesToNoteDomainCreatedAction() {
        NormalizedWebhookEvent event = parser.parse("""
                {"eventId":"evt-note-c","event":"notesCreated","resourceIds":[9001]}
                """, Map.of());
        assertEquals(NormalizedDomain.NOTE, event.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, event.normalizedAction());
    }

    @Test
    void notesUpdatedParsesToNoteDomainUpdatedAction() {
        NormalizedWebhookEvent event = parser.parse("""
                {"eventId":"evt-note-u","event":"notesUpdated","resourceIds":[9002]}
                """, Map.of());
        assertEquals(NormalizedDomain.NOTE, event.normalizedDomain());
        assertEquals(NormalizedAction.UPDATED, event.normalizedAction());
    }

    @Test
    void notesDeletedParsesToNoteDomainDeletedAction() {
        NormalizedWebhookEvent event = parser.parse("""
                {"eventId":"evt-note-d","event":"notesDeleted","resourceIds":[9003]}
                """, Map.of());
        assertEquals(NormalizedDomain.NOTE, event.normalizedDomain());
        assertEquals(NormalizedAction.DELETED, event.normalizedAction());
    }

    @Test
    void shouldAllowMissingResourceIdsForNonCallEvents() {
        String rawBody = """
                {
                  "eventId": "evt-people-created-missing-ids",
                  "event": "peopleCreated",
                  "uri": null
                }
                """;

        NormalizedWebhookEvent event = parser.parse(rawBody, Map.of());

        assertEquals("peopleCreated", event.sourceEventType());
        assertEquals(NormalizedDomain.PERSON, event.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, event.normalizedAction());
        assertEquals(0, event.payload().get("resourceIds").size());
        assertTrue(event.payload().get("uri").isNull());
        assertTrue(event.providerMeta().get("uri").isNull());
    }
}
