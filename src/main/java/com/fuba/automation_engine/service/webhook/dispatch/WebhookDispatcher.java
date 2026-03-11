package com.fuba.automation_engine.service.webhook.dispatch;

import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;

public interface WebhookDispatcher {

    void dispatch(NormalizedWebhookEvent event);
}
