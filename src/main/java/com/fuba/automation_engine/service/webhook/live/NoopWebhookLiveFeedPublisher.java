package com.fuba.automation_engine.service.webhook.live;

import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopWebhookLiveFeedPublisher implements WebhookLiveFeedPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopWebhookLiveFeedPublisher.class);

    @Override
    public void publish(WebhookLiveFeedEvent event) {
        log.debug("Noop live feed publish eventId={} source={}", event.eventId(), event.source());
    }
}
