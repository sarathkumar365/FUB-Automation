package com.fuba.automation_engine.service.webhook.dispatch;

import com.fuba.automation_engine.service.webhook.WebhookEventProcessorService;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncWebhookDispatcher implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AsyncWebhookDispatcher.class);

    private final WebhookEventProcessorService processorService;

    public AsyncWebhookDispatcher(WebhookEventProcessorService processorService) {
        this.processorService = processorService;
    }

    @Override
    @Async("webhookTaskExecutor")
    public void dispatch(NormalizedWebhookEvent event) {
        log.info("Async webhook dispatch started eventId={} source={}", event.eventId(), event.sourceSystem());
        try {
            processorService.process(event);
        } catch (RuntimeException ex) {
            log.error("Failed to process webhook eventId={} source={}", event.eventId(), event.sourceSystem(), ex);
        }
    }
}
