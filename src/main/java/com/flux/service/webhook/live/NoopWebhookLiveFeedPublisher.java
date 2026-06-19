package com.flux.service.webhook.live;

import com.flux.service.webhook.model.WebhookLiveFeedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopWebhookLiveFeedPublisher implements WebhookLiveFeedPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopWebhookLiveFeedPublisher.class);

    @Override
    public void publish(WebhookLiveFeedEvent event) {
        log.debug("Noop live feed publish eventId={} source={}", event.eventId(), event.source());
    }
}
