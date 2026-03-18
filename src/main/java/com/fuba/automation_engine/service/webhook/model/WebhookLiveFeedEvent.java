package com.fuba.automation_engine.service.webhook.model;

import java.time.OffsetDateTime;

public record WebhookLiveFeedEvent(
        Long id,
        String eventId,
        WebhookSource source,
        String eventType,
        WebhookEventStatus status,
        OffsetDateTime receivedAt) {
}
