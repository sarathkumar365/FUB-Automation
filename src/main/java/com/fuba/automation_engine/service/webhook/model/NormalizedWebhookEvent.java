package com.fuba.automation_engine.service.webhook.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

public record NormalizedWebhookEvent(
        WebhookSource sourceSystem,
        String eventId,
        String sourceEventType,
        OffsetDateTime occurredAt,
        String sourcePersonId,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        JsonNode providerMeta,
        WebhookEventStatus status,
        JsonNode payload,
        OffsetDateTime receivedAt,
        String payloadHash,
        Long webhookEventId) {

    /**
     * Returns a copy of this event with the supplied persisted {@code webhook_events.id}.
     * Used by {@code WebhookIngressService} to attach the freshly-saved entity's PK
     * before dispatching the event downstream.
     */
    public NormalizedWebhookEvent withWebhookEventId(Long id) {
        return new NormalizedWebhookEvent(
                sourceSystem,
                eventId,
                sourceEventType,
                occurredAt,
                sourcePersonId,
                normalizedDomain,
                normalizedAction,
                providerMeta,
                status,
                payload,
                receivedAt,
                payloadHash,
                id);
    }
}
