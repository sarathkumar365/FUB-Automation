package com.fuba.automation_engine.service.webhook.dispatch;

import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopWebhookDispatcher implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NoopWebhookDispatcher.class);

    @Override
    public void dispatch(NormalizedWebhookEvent event) {
        log.info("Noop dispatch for webhook eventId={}, source={}", event.eventId(), event.source());
    }
}
