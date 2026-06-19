package com.flux.service.webhook.dispatch;

import com.flux.service.webhook.model.NormalizedWebhookEvent;

public interface WebhookDispatcher {

    void dispatch(NormalizedWebhookEvent event);
}
