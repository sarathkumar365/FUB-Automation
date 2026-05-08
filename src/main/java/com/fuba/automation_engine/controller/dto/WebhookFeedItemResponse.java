package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookFeedItemResponse(
        Long id,
        String eventId,
        WebhookSource source,
        String eventType,
        EventSupportState catalogState,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        WebhookEventStatus status,
        OffsetDateTime receivedAt,
        @JsonSerialize(as = JsonNode.class)
        JsonNode payload) {
}
