package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.controller.dto.WebhookEventDetailResponse;
import com.fuba.automation_engine.controller.dto.WebhookFeedItemResponse;
import com.fuba.automation_engine.controller.dto.WebhookFeedPageResponse;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookFeedQueryException;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedRow;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AdminWebhookService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private final WebhookFeedReadRepository feedReadRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookFeedCursorCodec cursorCodec;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminWebhookService(
            WebhookFeedReadRepository feedReadRepository,
            WebhookEventRepository webhookEventRepository,
            WebhookFeedCursorCodec cursorCodec,
            ObjectMapper objectMapper,
            Clock clock) {
        this.feedReadRepository = feedReadRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.cursorCodec = cursorCodec;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WebhookFeedPageResponse list(WebhookFeedQuery query) {
        int limit = normalizeLimit(query.limit());
        validateRange(query.from(), query.to());
        String normalizedEventType = normalizeEventType(query.eventType());
        WebhookFeedCursorCodec.Cursor cursor = cursorCodec.decode(query.cursor());

        List<WebhookFeedItemResponse> items = feedReadRepository.fetch(new WebhookFeedReadQuery(
                        query.source(),
                        query.status(),
                        normalizedEventType,
                        query.from(),
                        query.to(),
                        cursor.receivedAt(),
                        cursor.id(),
                        limit + 1,
                        query.includePayload()))
                .stream()
                .map(this::toFeedItem)
                .toList();

        return buildPage(items, limit);
    }

    public Optional<WebhookEventDetailResponse> findDetail(long id) {
        return webhookEventRepository.findById(id).map(this::toDetailResponse);
    }

    public List<String> listDistinctEventTypes() {
        return webhookEventRepository.findDistinctEventTypes();
    }

    private WebhookFeedPageResponse buildPage(List<WebhookFeedItemResponse> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<WebhookFeedItemResponse> pageItems = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            WebhookFeedItemResponse tail = pageItems.get(pageItems.size() - 1);
            nextCursor = cursorCodec.encode(tail.receivedAt(), tail.id());
        }

        return new WebhookFeedPageResponse(
                List.copyOf(pageItems),
                nextCursor,
                OffsetDateTime.now(clock));
    }

    private WebhookFeedItemResponse toFeedItem(WebhookFeedRow row) {
        return new WebhookFeedItemResponse(
                row.id(),
                row.eventId(),
                row.source(),
                row.eventType(),
                row.catalogState(),
                row.normalizedDomain(),
                row.normalizedAction(),
                row.status(),
                row.receivedAt(),
                normalizePayload(row.payload()));
    }

    private WebhookEventDetailResponse toDetailResponse(WebhookEventEntity entity) {
        return new WebhookEventDetailResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getSource(),
                entity.getEventType(),
                entity.getCatalogState(),
                entity.getNormalizedDomain(),
                entity.getNormalizedAction(),
                entity.getStatus(),
                entity.getPayloadHash(),
                normalizePayload(entity.getPayload()),
                entity.getReceivedAt());
    }

    private JsonNode normalizePayload(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload.toString());
        } catch (Exception ex) {
            return payload;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return limit;
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        return eventType.trim();
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidWebhookFeedQueryException("'from' must be before or equal to 'to'");
        }
    }

    public record WebhookFeedQuery(
            WebhookSource source,
            WebhookEventStatus status,
            String eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit,
            String cursor,
            boolean includePayload) {
    }
}
