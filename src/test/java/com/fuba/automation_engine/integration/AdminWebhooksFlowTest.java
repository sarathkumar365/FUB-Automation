package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminWebhooksFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
    }

    @Test
    void shouldListAndDetailWebhookAfterIngest() throws Exception {
        String body = """
                {
                  "eventId": "evt-admin-1",
                  "event": "callsCreated",
                  "resourceIds": [201],
                  "uri": null
                }
                """;
        String signature = hmacHex(base64(body), "test-signing-key");

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", signature)
                        .content(body))
                .andExpect(status().isAccepted());

        WebhookEventEntity entity = webhookEventRepository.findBySourceAndEventId(WebhookSource.FUB, "evt-admin-1")
                .orElseThrow();

        mockMvc.perform(get("/admin/webhooks").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").value("evt-admin-1"));

        mockMvc.perform(get("/admin/webhooks/" + entity.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entity.getId()))
                .andExpect(jsonPath("$.payloadHash").value(entity.getPayloadHash()))
                .andExpect(jsonPath("$.payload").exists());
    }

    @Test
    void shouldProvideCursorContinuityWithoutOverlap() throws Exception {
        OffsetDateTime tiedTime = OffsetDateTime.parse("2026-03-17T11:00:00Z");
        WebhookEventEntity first = webhookEventRepository.saveAndFlush(buildEntity("evt-cursor-1", "hash-1", tiedTime));
        WebhookEventEntity second = webhookEventRepository.saveAndFlush(buildEntity("evt-cursor-2", "hash-2", tiedTime));
        WebhookEventEntity third = webhookEventRepository.saveAndFlush(buildEntity("evt-cursor-3", "hash-3", tiedTime.minusMinutes(1)));

        MvcResult firstResult = mockMvc.perform(get("/admin/webhooks").param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstPage = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        assertEquals(2, firstPage.get("items").size());
        assertNotNull(firstPage.get("nextCursor"));
        String nextCursor = firstPage.get("nextCursor").asText();
        assertTrue(!nextCursor.isBlank());

        List<Long> firstIds = List.of(
                firstPage.get("items").get(0).get("id").asLong(),
                firstPage.get("items").get(1).get("id").asLong());

        MvcResult secondResult = mockMvc.perform(get("/admin/webhooks")
                        .param("limit", "2")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondPage = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        assertEquals(1, secondPage.get("items").size());

        long secondPageId = secondPage.get("items").get(0).get("id").asLong();
        assertEquals(third.getId(), secondPageId);
        assertNotEquals(firstIds.get(0), secondPageId);
        assertNotEquals(firstIds.get(1), secondPageId);
        assertTrue(firstIds.contains(first.getId()) || firstIds.contains(second.getId()));
    }

    @Test
    void shouldApplyFeedFiltersAndPayloadFlagInFlow() throws Exception {
        webhookEventRepository.saveAndFlush(buildEntity("evt-filter-1", "hash-filter-1", OffsetDateTime.parse("2026-03-17T09:00:00Z")));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", "callsUpdated");
        WebhookEventEntity event2 = new WebhookEventEntity();
        event2.setSource(WebhookSource.FUB);
        event2.setEventId("evt-filter-2");
        event2.setEventType("callsUpdated");
        event2.setStatus(WebhookEventStatus.RECEIVED);
        event2.setPayload(payload);
        event2.setPayloadHash("hash-filter-2");
        event2.setReceivedAt(OffsetDateTime.parse("2026-03-17T10:00:00Z"));
        webhookEventRepository.saveAndFlush(event2);

        mockMvc.perform(get("/admin/webhooks")
                        .param("source", "FUB")
                        .param("status", "RECEIVED")
                        .param("eventType", "callsUpdated")
                        .param("from", "2026-03-17T09:30:00Z")
                        .param("to", "2026-03-17T10:30:00Z")
                        .param("limit", "5")
                        .param("includePayload", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("evt-filter-2"))
                .andExpect(jsonPath("$.items[0].payload.nodeType").value("OBJECT"))
                .andExpect(jsonPath("$.items[0].payload.object").value(true))
                .andExpect(jsonPath("$.serverTime").exists());
    }

    private WebhookEventEntity buildEntity(String eventId, String payloadHash, OffsetDateTime receivedAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", "callsCreated");

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(WebhookSource.FUB);
        entity.setEventId(eventId);
        entity.setEventType("callsCreated");
        entity.setStatus(WebhookEventStatus.RECEIVED);
        entity.setPayload(payload);
        entity.setPayloadHash(payloadHash);
        entity.setReceivedAt(receivedAt);
        return entity;
    }

    private String hmacHex(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String base64(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
