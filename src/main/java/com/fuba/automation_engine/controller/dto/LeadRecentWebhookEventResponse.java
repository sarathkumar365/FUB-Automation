package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadRecentWebhookEventResponse(
        Long id,
        WebhookSource source,
        String eventType,
        WebhookEventStatus status,
        OffsetDateTime receivedAt) {
}
