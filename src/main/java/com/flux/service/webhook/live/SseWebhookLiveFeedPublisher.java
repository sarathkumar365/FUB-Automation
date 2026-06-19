package com.flux.service.webhook.live;

import com.flux.service.webhook.model.WebhookLiveFeedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SseWebhookLiveFeedPublisher implements WebhookLiveFeedPublisher {

    private final WebhookSseHub webhookSseHub;

    public SseWebhookLiveFeedPublisher(WebhookSseHub webhookSseHub) {
        this.webhookSseHub = webhookSseHub;
    }

    @Override
    public void publish(WebhookLiveFeedEvent event) {
        webhookSseHub.publish(event);
    }
}

