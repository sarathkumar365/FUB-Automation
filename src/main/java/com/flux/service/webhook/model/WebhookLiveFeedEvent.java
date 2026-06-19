package com.flux.service.webhook.model;

import java.time.OffsetDateTime;

public record WebhookLiveFeedEvent(
        Long id,
        String eventId,
        WebhookSource source,
        String eventType,
        WebhookEventStatus status,
        OffsetDateTime receivedAt) {
}
