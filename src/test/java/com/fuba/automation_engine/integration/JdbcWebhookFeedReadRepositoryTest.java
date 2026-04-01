package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedRow;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class JdbcWebhookFeedReadRepositoryTest {

    @Autowired
    private WebhookFeedReadRepository feedReadRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
    }

    @Test
    void shouldFetchWithoutFiltersAndWithoutCursor() {
        WebhookEventEntity older = webhookEventRepository.saveAndFlush(buildEntity("evt-a", "hash-a", "callsCreated", OffsetDateTime.parse("2026-03-17T10:00:00Z")));
        WebhookEventEntity newer = webhookEventRepository.saveAndFlush(buildEntity("evt-b", "hash-b", "callsCreated", OffsetDateTime.parse("2026-03-17T11:00:00Z")));

        List<WebhookFeedRow> rows = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                false));

        assertEquals(2, rows.size());
        assertEquals(newer.getId(), rows.get(0).id());
        assertEquals(older.getId(), rows.get(1).id());
        assertEquals(EventSupportState.SUPPORTED, rows.get(0).catalogState());
        assertEquals(NormalizedDomain.CALL, rows.get(0).normalizedDomain());
        assertEquals(NormalizedAction.CREATED, rows.get(0).normalizedAction());
        assertNull(rows.get(0).payload());
    }

    @Test
    void shouldApplyEachFilterAndCombinedFilters() {
        OffsetDateTime base = OffsetDateTime.parse("2026-03-17T12:00:00Z");
        webhookEventRepository.saveAndFlush(buildEntity("evt-c1", "hash-c1", "callsCreated", base.minusMinutes(4)));
        webhookEventRepository.saveAndFlush(buildEntity("evt-c2", "hash-c2", "callsUpdated", base.minusMinutes(3)));
        webhookEventRepository.saveAndFlush(buildEntity("evt-c3", "hash-c3", "callsCreated", base.minusMinutes(2)));

        List<WebhookFeedRow> onlyEventType = feedReadRepository.fetch(new WebhookFeedReadQuery(
                WebhookSource.FUB,
                WebhookEventStatus.RECEIVED,
                "callsCreated",
                null,
                null,
                null,
                null,
                10,
                false));
        assertEquals(2, onlyEventType.size());

        List<WebhookFeedRow> combined = feedReadRepository.fetch(new WebhookFeedReadQuery(
                WebhookSource.FUB,
                WebhookEventStatus.RECEIVED,
                "callsCreated",
                base.minusMinutes(3),
                base.minusMinutes(1),
                null,
                null,
                10,
                false));
        assertEquals(1, combined.size());
        assertEquals("evt-c3", combined.get(0).eventId());
    }

    @Test
    void shouldContinueWithCursorUsingReceivedAtAndIdTieBreak() {
        OffsetDateTime tied = OffsetDateTime.parse("2026-03-17T13:00:00Z");
        webhookEventRepository.saveAndFlush(buildEntity("evt-t1", "hash-t1", "callsCreated", tied));
        webhookEventRepository.saveAndFlush(buildEntity("evt-t2", "hash-t2", "callsCreated", tied));
        WebhookEventEntity third = webhookEventRepository.saveAndFlush(buildEntity("evt-t3", "hash-t3", "callsCreated", tied.minusMinutes(1)));

        List<WebhookFeedRow> firstPagePlusOne = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                3,
                false));
        assertEquals(3, firstPagePlusOne.size());

        WebhookFeedRow secondItem = firstPagePlusOne.get(1);

        List<WebhookFeedRow> continuation = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                secondItem.receivedAt(),
                secondItem.id(),
                10,
                false));

        assertEquals(1, continuation.size());
        assertEquals(third.getId(), continuation.get(0).id());
    }

    @Test
    void shouldIncludePayloadOnlyWhenRequested() {
        webhookEventRepository.saveAndFlush(buildEntity("evt-p", "hash-p", "callsCreated", OffsetDateTime.parse("2026-03-17T14:00:00Z")));

        List<WebhookFeedRow> withoutPayload = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                false));
        List<WebhookFeedRow> withPayload = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                true));

        assertEquals(1, withoutPayload.size());
        assertEquals(1, withPayload.size());
        assertNull(withoutPayload.get(0).payload());
        assertNotNull(withPayload.get(0).payload());
        assertEquals("callsCreated", withPayload.get(0).payload().get("eventType").asText());
    }

    @Test
    void shouldSupportLimitPlusOneFetchShape() {
        OffsetDateTime start = OffsetDateTime.parse("2026-03-17T15:00:00Z");
        webhookEventRepository.saveAndFlush(buildEntity("evt-l1", "hash-l1", "callsCreated", start));
        webhookEventRepository.saveAndFlush(buildEntity("evt-l2", "hash-l2", "callsCreated", start.minusMinutes(1)));
        webhookEventRepository.saveAndFlush(buildEntity("evt-l3", "hash-l3", "callsCreated", start.minusMinutes(2)));

        List<WebhookFeedRow> rows = feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                3,
                false));

        assertEquals(3, rows.size());
        assertTrue(rows.get(0).receivedAt().isAfter(rows.get(1).receivedAt())
                || rows.get(0).receivedAt().isEqual(rows.get(1).receivedAt()));
    }

    @Test
    void shouldRejectPartialCursorWhenOnlyReceivedAtProvided() {
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-03-17T15:00:00Z"),
                null,
                10,
                false)));
    }

    @Test
    void shouldRejectPartialCursorWhenOnlyCursorIdProvided() {
        assertThrows(InvalidDataAccessApiUsageException.class, () -> feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                99L,
                10,
                false)));
    }

    @Test
    void shouldAcceptCompleteCursorContract() {
        assertDoesNotThrow(() -> feedReadRepository.fetch(new WebhookFeedReadQuery(
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-03-17T15:00:00Z"),
                99L,
                10,
                false)));
    }

    private WebhookEventEntity buildEntity(
            String eventId,
            String payloadHash,
            String eventType,
            OffsetDateTime receivedAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", eventType);

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(WebhookSource.FUB);
        entity.setEventId(eventId);
        entity.setEventType(eventType);
        entity.setCatalogState(EventSupportState.SUPPORTED);
        entity.setNormalizedDomain(NormalizedDomain.CALL);
        entity.setNormalizedAction(NormalizedAction.CREATED);
        entity.setStatus(WebhookEventStatus.RECEIVED);
        entity.setPayload(payload);
        entity.setPayloadHash(payloadHash);
        entity.setReceivedAt(receivedAt);
        return entity;
    }
}
