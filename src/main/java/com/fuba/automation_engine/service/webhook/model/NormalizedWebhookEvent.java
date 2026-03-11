package com.fuba.automation_engine.service.webhook.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record NormalizedWebhookEvent(
        WebhookSource source,
        String eventId,
        WebhookEventStatus status,
        JsonNode payload,
        OffsetDateTime receivedAt,
        String payloadHash) {
}
