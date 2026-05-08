package com.fuba.automation_engine.service.webhook.support;

import com.fuba.automation_engine.service.webhook.model.WebhookSource;

public interface WebhookEventSupportResolver {

    EventSupportResolution resolve(WebhookSource sourceSystem, String sourceEventType);
}
