package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class WebhookEventRepositoryTest {

    @Autowired
    private WebhookEventRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPersistEventWithReceivedStatus() {
        WebhookEventEntity entity = buildEntity("evt-1", "hash-1", OffsetDateTime.now());

        WebhookEventEntity saved = repository.saveAndFlush(entity);

        assertEquals(WebhookEventStatus.RECEIVED, saved.getStatus());
        assertEquals("callsCreated", saved.getEventType());
        assertTrue(repository.existsBySourceAndEventId(WebhookSource.FUB, "evt-1"));
    }

    @Test
    void shouldRejectDuplicateSourceAndEventId() {
        repository.saveAndFlush(buildEntity("evt-dup", "hash-1", OffsetDateTime.now()));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(buildEntity("evt-dup", "hash-2", OffsetDateTime.now().plusSeconds(1))));
    }

    @Test
    void shouldQueryByStatusAndReceivedAtOrder() {
        repository.saveAndFlush(buildEntity("evt-2", "hash-2", OffsetDateTime.now().minusSeconds(5)));
        repository.saveAndFlush(buildEntity("evt-3", "hash-3", OffsetDateTime.now()));

        List<WebhookEventEntity> events = repository.findByStatusOrderByReceivedAtAsc(WebhookEventStatus.RECEIVED);

        assertEquals(2, events.size());
        assertTrue(events.get(0).getReceivedAt().isBefore(events.get(1).getReceivedAt())
                || events.get(0).getReceivedAt().isEqual(events.get(1).getReceivedAt()));
    }

    @Test
    void shouldFindBySourceAndEventId() {
        repository.saveAndFlush(buildEntity("evt-find", "hash-find", OffsetDateTime.now()));

        WebhookEventEntity found = repository.findBySourceAndEventId(WebhookSource.FUB, "evt-find").orElseThrow();

        assertEquals("evt-find", found.getEventId());
        assertEquals("callsCreated", found.getEventType());
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
}
