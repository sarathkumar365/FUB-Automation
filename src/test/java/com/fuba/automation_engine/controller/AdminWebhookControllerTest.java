package com.fuba.automation_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminWebhookControllerTest {

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
    void shouldReturnListWithDefaultIncludePayloadFalse() throws Exception {
        webhookEventRepository.saveAndFlush(buildEntity("evt-ctrl-1", "callsCreated", OffsetDateTime.parse("2026-03-17T11:00:00Z")));

        mockMvc.perform(get("/admin/webhooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").value("evt-ctrl-1"))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void shouldReturnListWithPayloadWhenRequested() throws Exception {
        webhookEventRepository.saveAndFlush(buildEntity("evt-ctrl-2", "callsCreated", OffsetDateTime.parse("2026-03-17T12:00:00Z")));

        mockMvc.perform(get("/admin/webhooks").param("includePayload", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload.nodeType").value("OBJECT"))
                .andExpect(jsonPath("$.items[0].payload.object").value(true));
    }

    @Test
    void shouldReturnBadRequestForInvalidCursorOrRange() throws Exception {
        mockMvc.perform(get("/admin/webhooks").param("cursor", "bad-cursor"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/admin/webhooks")
                        .param("from", "2026-03-18T00:00:00Z")
                        .param("to", "2026-03-17T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForMalformedEnumFilters() throws Exception {
        mockMvc.perform(get("/admin/webhooks").param("status", "BROKEN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldApplyFiltersAndLimit() throws Exception {
        webhookEventRepository.saveAndFlush(buildEntity("evt-ctrl-4", "callsCreated", OffsetDateTime.parse("2026-03-17T10:00:00Z")));
        webhookEventRepository.saveAndFlush(buildEntity("evt-ctrl-5", "callsUpdated", OffsetDateTime.parse("2026-03-17T11:00:00Z")));
        webhookEventRepository.saveAndFlush(buildEntity("evt-ctrl-6", "callsCreated", OffsetDateTime.parse("2026-03-17T12:00:00Z")));

        mockMvc.perform(get("/admin/webhooks")
                        .param("source", "FUB")
                        .param("status", "RECEIVED")
                        .param("eventType", "callsCreated")
                        .param("from", "2026-03-17T11:00:00Z")
                        .param("to", "2026-03-17T12:00:00Z")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value("evt-ctrl-6"))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void shouldReturnDetailWhenFound() throws Exception {
        WebhookEventEntity saved = webhookEventRepository.saveAndFlush(
                buildEntity("evt-ctrl-3", "callsCreated", OffsetDateTime.parse("2026-03-17T13:00:00Z")));

        mockMvc.perform(get("/admin/webhooks/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.payloadHash").value(saved.getPayloadHash()))
                .andExpect(jsonPath("$.payload.nodeType").value("OBJECT"))
                .andExpect(jsonPath("$.payload.object").value(true));
    }

    @Test
    void shouldReturnNotFoundForMissingDetail() throws Exception {
        mockMvc.perform(get("/admin/webhooks/404404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestForMalformedEnumFiltersOnStream() throws Exception {
        mockMvc.perform(get("/admin/webhooks/stream").param("status", "BROKEN"))
                .andExpect(status().isBadRequest());
    }

    private WebhookEventEntity buildEntity(String eventId, String eventType, OffsetDateTime receivedAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", eventType);

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(WebhookSource.FUB);
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setStatus(WebhookEventStatus.RECEIVED);
        entity.setPayload(payload);
        entity.setPayloadHash("hash-" + eventId);
        entity.setReceivedAt(receivedAt);
        return entity;
    }
}
