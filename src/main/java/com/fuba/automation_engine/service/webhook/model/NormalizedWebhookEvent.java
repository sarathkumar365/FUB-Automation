package com.fuba.automation_engine.service.webhook.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record NormalizedWebhookEvent(
        WebhookSource sourceSystem,
        String eventId,
        String sourceEventType,
        OffsetDateTime occurredAt,
        String sourceLeadId,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        JsonNode providerMeta,
        WebhookEventStatus status,
        JsonNode payload,
        OffsetDateTime receivedAt,
        String payloadHash) {
}
