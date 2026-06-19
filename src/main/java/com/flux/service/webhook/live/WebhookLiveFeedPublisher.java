package com.flux.service.webhook.live;

import com.flux.service.webhook.model.WebhookLiveFeedEvent;

public interface WebhookLiveFeedPublisher {

    void publish(WebhookLiveFeedEvent event);
}
