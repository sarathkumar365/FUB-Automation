package com.flux.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flux.service.webhook.model.WebhookEventStatus;
import com.flux.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonRecentWebhookEventResponse(
        Long id,
        WebhookSource source,
        String eventType,
        WebhookEventStatus status,
        OffsetDateTime receivedAt) {
}
