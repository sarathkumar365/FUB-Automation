package com.fuba.automation_engine.service.webhook.live;

import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;

public interface WebhookLiveFeedPublisher {

    void publish(WebhookLiveFeedEvent event);
}
