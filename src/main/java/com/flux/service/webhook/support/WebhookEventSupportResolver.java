package com.flux.service.webhook.support;

import com.flux.service.webhook.model.WebhookSource;

public interface WebhookEventSupportResolver {

    EventSupportResolution resolve(WebhookSource sourceSystem, String sourceEventType);
}
