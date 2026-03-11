package com.fuba.automation_engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.parse.FubWebhookParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookParserTest {

    private final FubWebhookParser parser = new FubWebhookParser(new ObjectMapper());

    @Test
    void shouldParseResourceIdsAndNullUri() {
        String rawBody = """
                {
                  "eventId": "evt_123",
                  "event": "callsCreated",
                  "resourceIds": [111, 222],
                  "uri": null
                }
                """;

        NormalizedWebhookEvent event = parser.parse(rawBody, Map.of("FUB-Signature", "sig"));

        assertEquals("evt_123", event.eventId());
        assertEquals(2, event.payload().get("resourceIds").size());
        assertNotNull(event.payload().get("uri"));
        assertTrue(event.payload().get("uri").isNull());
    }

    @Test
    void shouldRejectWhenResourceIdsMissing() {
        String rawBody = """
                {
                  "event": "callsCreated"
                }
                """;

        assertThrows(MalformedWebhookPayloadException.class, () -> parser.parse(rawBody, Map.of()));
    }

    @Test
    void shouldRejectWhenEventMissing() {
        String rawBody = """
                {
                  "resourceIds": [123]
                }
                """;

        assertThrows(MalformedWebhookPayloadException.class, () -> parser.parse(rawBody, Map.of()));
    }
}
