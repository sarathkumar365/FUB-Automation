package com.fuba.automation_engine.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;

public interface WebhookFeedReadRepository {

    List<WebhookFeedRow> fetch(WebhookFeedReadQuery query);

    record WebhookFeedReadQuery(
            WebhookSource source,
            WebhookEventStatus status,
            String eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorReceivedAt,
            Long cursorId,
            int limit,
            boolean includePayload) {
    }

    record WebhookFeedRow(
            Long id,
            String eventId,
            WebhookSource source,
            String eventType,
            EventSupportState catalogState,
            NormalizedDomain normalizedDomain,
            NormalizedAction normalizedAction,
            WebhookEventStatus status,
            OffsetDateTime receivedAt,
            JsonNode payload) {
    }
}
