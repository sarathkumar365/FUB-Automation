package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.controller.dto.WebhookFeedPageResponse;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookFeedQueryException;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedRow;
import com.fuba.automation_engine.service.webhook.AdminWebhookService.WebhookFeedQuery;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminWebhookServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InMemoryWebhookFeedReadRepository feedReadRepository;
    private WebhookEventRepository webhookEventRepository;
    private WebhookFeedCursorCodec cursorCodec;
    private AdminWebhookService service;

    @BeforeEach
    void setUp() {
        feedReadRepository = new InMemoryWebhookFeedReadRepository();
        webhookEventRepository = Mockito.mock(WebhookEventRepository.class);
        cursorCodec = new WebhookFeedCursorCodec(OBJECT_MAPPER);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-17T20:00:00Z"), ZoneOffset.UTC);
        service = new AdminWebhookService(feedReadRepository, webhookEventRepository, cursorCodec, OBJECT_MAPPER, fixedClock);
    }

    @Test
    void shouldEncodeAndDecodeCursorRoundTrip() {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-03-17T19:00:00Z");
        String cursor = cursorCodec.encode(receivedAt, 99L);

        WebhookFeedCursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        assertEquals(receivedAt, decoded.receivedAt());
        assertEquals(99L, decoded.id());
    }

    @Test
    void shouldRejectInvalidCursor() {
        assertThrows(
                InvalidWebhookFeedQueryException.class,
                () -> service.list(new WebhookFeedQuery(
                        null,
                        null,
                        null,
                        null,
                        null,
                        10,
                        "bad_cursor",
                        false)));
    }

    @Test
    void shouldRejectInvalidRange() {
        assertThrows(
                InvalidWebhookFeedQueryException.class,
                () -> service.list(new WebhookFeedQuery(
                        null,
                        null,
                        null,
                        OffsetDateTime.parse("2026-03-18T00:00:00Z"),
                        OffsetDateTime.parse("2026-03-17T00:00:00Z"),
                        10,
                        null,
                        false)));
    }

    @Test
    void shouldApplyLimitDefaultAndCap() {
        feedReadRepository.rows.addAll(buildRows(230, OffsetDateTime.parse("2026-03-17T19:00:00Z"), "callsCreated"));

        WebhookFeedPageResponse defaultPage = service.list(new WebhookFeedQuery(
                null, null, null, null, null, null, null, false));
        WebhookFeedPageResponse cappedPage = service.list(new WebhookFeedQuery(
                null, null, null, null, null, 999, null, false));

        assertEquals(50, defaultPage.items().size());
        assertEquals(200, cappedPage.items().size());
    }

    @Test
    void shouldGenerateNextCursorAndContinueWithoutOverlap() {
        OffsetDateTime sameTime = OffsetDateTime.parse("2026-03-17T18:00:00Z");
        feedReadRepository.rows.add(new WebhookFeedRow(
                105L, "evt-105", WebhookSource.FUB, "callsCreated", EventSupportState.SUPPORTED, NormalizedDomain.CALL, NormalizedAction.CREATED, WebhookEventStatus.RECEIVED, sameTime, payload("A")));
        feedReadRepository.rows.add(new WebhookFeedRow(
                104L, "evt-104", WebhookSource.FUB, "callsCreated", EventSupportState.SUPPORTED, NormalizedDomain.CALL, NormalizedAction.CREATED, WebhookEventStatus.RECEIVED, sameTime, payload("B")));
        feedReadRepository.rows.add(new WebhookFeedRow(
                103L, "evt-103", WebhookSource.FUB, "callsCreated", EventSupportState.SUPPORTED, NormalizedDomain.CALL, NormalizedAction.CREATED, WebhookEventStatus.RECEIVED, sameTime.minusMinutes(1), payload("C")));

        WebhookFeedPageResponse firstPage = service.list(new WebhookFeedQuery(
                null, null, null, null, null, 2, null, false));
        assertEquals(2, firstPage.items().size());
        assertNotNull(firstPage.nextCursor());
        assertNull(firstPage.items().get(0).payload());

        WebhookFeedPageResponse secondPage = service.list(new WebhookFeedQuery(
                null, null, null, null, null, 2, firstPage.nextCursor(), false));
        assertEquals(1, secondPage.items().size());
        assertEquals(103L, secondPage.items().get(0).id());
    }

    @Test
    void shouldIncludePayloadWhenRequested() {
        feedReadRepository.rows.add(new WebhookFeedRow(
                90L,
                "evt-90",
                WebhookSource.FUB,
                "callsCreated",
                EventSupportState.SUPPORTED,
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                WebhookEventStatus.RECEIVED,
                OffsetDateTime.parse("2026-03-17T10:00:00Z"),
                payload("X")));

        WebhookFeedPageResponse page = service.list(new WebhookFeedQuery(
                null, null, null, null, null, 10, null, true));

        assertEquals(1, page.items().size());
        assertNotNull(page.items().get(0).payload());
        assertEquals("X", page.items().get(0).payload().get("marker").asText());
    }

    @Test
    void shouldDelegateListDistinctEventTypesToRepository() {
        Mockito.when(webhookEventRepository.findDistinctEventTypes())
                .thenReturn(List.of("callsCreated", "callsUpdated"));

        List<String> result = service.listDistinctEventTypes();

        assertEquals(List.of("callsCreated", "callsUpdated"), result);
    }

    @Test
    void shouldReturnDetailWhenEntityExists() {
        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setId(42L);
        entity.setEventId("evt-42");
        entity.setSource(WebhookSource.FUB);
        entity.setEventType("callsCreated");
        entity.setCatalogState(EventSupportState.SUPPORTED);
        entity.setNormalizedDomain(NormalizedDomain.CALL);
        entity.setNormalizedAction(NormalizedAction.CREATED);
        entity.setStatus(WebhookEventStatus.RECEIVED);
        entity.setPayloadHash("hash-42");
        entity.setPayload(payload("detail"));
        entity.setReceivedAt(OffsetDateTime.parse("2026-03-17T10:00:00Z"));
        Mockito.when(webhookEventRepository.findById(42L)).thenReturn(Optional.of(entity));

        assertEquals("evt-42", service.findDetail(42L).orElseThrow().eventId());
        assertEquals(EventSupportState.SUPPORTED, service.findDetail(42L).orElseThrow().catalogState());
    }

    private List<WebhookFeedRow> buildRows(int count, OffsetDateTime start, String eventType) {
        List<WebhookFeedRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = 500L - i;
            rows.add(new WebhookFeedRow(
                    id,
                    "evt-" + id,
                    WebhookSource.FUB,
                    eventType,
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.CALL,
                    NormalizedAction.CREATED,
                    WebhookEventStatus.RECEIVED,
                    start.minusSeconds(i),
                    payload("m-" + id)));
        }
        return rows;
    }

    private ObjectNode payload(String marker) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "callsCreated");
        payload.put("marker", marker);
        return payload;
    }

    private static class InMemoryWebhookFeedReadRepository implements WebhookFeedReadRepository {
        private final List<WebhookFeedRow> rows = new ArrayList<>();

        @Override
        public List<WebhookFeedRow> fetch(WebhookFeedReadQuery query) {
            return rows.stream()
                    .filter(row -> query.source() == null || row.source().equals(query.source()))
                    .filter(row -> query.status() == null || row.status().equals(query.status()))
                    .filter(row -> query.eventType() == null || row.eventType().equals(query.eventType()))
                    .filter(row -> query.from() == null || !row.receivedAt().isBefore(query.from()))
                    .filter(row -> query.to() == null || !row.receivedAt().isAfter(query.to()))
                    .filter(row -> query.cursorReceivedAt() == null
                            || row.receivedAt().isBefore(query.cursorReceivedAt())
                            || (row.receivedAt().isEqual(query.cursorReceivedAt()) && row.id() < query.cursorId()))
                    .sorted(Comparator.comparing(WebhookFeedRow::receivedAt).reversed().thenComparing(WebhookFeedRow::id, Comparator.reverseOrder()))
                    .map(row -> query.includePayload() ? row : new WebhookFeedRow(
                            row.id(),
                            row.eventId(),
                            row.source(),
                            row.eventType(),
                            row.catalogState(),
                            row.normalizedDomain(),
                            row.normalizedAction(),
                            row.status(),
                            row.receivedAt(),
                            null))
                    .limit(query.limit())
                    .toList();
        }
    }
}
