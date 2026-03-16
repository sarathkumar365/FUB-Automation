package com.fuba.automation_engine.service.webhook;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookSignatureException;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.exception.webhook.UnsupportedWebhookSourceException;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.dispatch.WebhookDispatcher;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookIngressResult;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.webhook.parse.WebhookParser;
import com.fuba.automation_engine.service.webhook.security.WebhookSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WebhookIngressService {

    private static final String EVENT_CALLS_CREATED = "callsCreated";

    private final List<WebhookSignatureVerifier> signatureVerifiers;
    private final List<WebhookParser> parsers;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDispatcher webhookDispatcher;
    private final WebhookProperties webhookProperties;

    public WebhookIngressService(
            List<WebhookSignatureVerifier> signatureVerifiers,
            List<WebhookParser> parsers,
            WebhookEventRepository webhookEventRepository,
            WebhookDispatcher webhookDispatcher,
            WebhookProperties webhookProperties) {
        this.signatureVerifiers = signatureVerifiers;
        this.parsers = parsers;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.webhookProperties = webhookProperties;
    }

    public WebhookIngressResult ingest(String sourcePath, String rawBody, Map<String, String> headers) {
        WebhookSource source = WebhookSource.fromPathValue(sourcePath);
        if (source == null) {
            throw new UnsupportedWebhookSourceException("Unsupported webhook source: " + sourcePath);
        }

        if (source == WebhookSource.FUB && !webhookProperties.getSources().getFub().isEnabled()) {
            throw new UnsupportedWebhookSourceException("Webhook source not enabled: " + sourcePath);
        }

        if (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > webhookProperties.getMaxBodyBytes()) {
            throw new MalformedWebhookPayloadException("Payload exceeds max allowed body size");
        }

        WebhookSignatureVerifier verifier = signatureVerifiers.stream()
                .filter(it -> it.supports(source))
                .findFirst()
                .orElseThrow(() -> new UnsupportedWebhookSourceException("No signature verifier configured for source: " + source));

        if (!verifier.verify(rawBody, headers)) {
            throw new InvalidWebhookSignatureException("Webhook signature validation failed");
        }

        WebhookParser parser = parsers.stream()
                .filter(it -> it.supports(source))
                .findFirst()
                .orElseThrow(() -> new UnsupportedWebhookSourceException("No parser configured for source: " + source));

        NormalizedWebhookEvent event = parser.parse(rawBody, headers);
        String eventType = extractEventType(event);
        String acceptedMessage = EVENT_CALLS_CREATED.equals(eventType)
                ? "Webhook accepted for async processing"
                : "Event type not supported yet: " + eventType;

        if (event.eventId() != null && !event.eventId().isBlank()
                && webhookEventRepository.existsBySourceAndEventId(event.source(), event.eventId())) {
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        if ((event.eventId() == null || event.eventId().isBlank())
                && event.payloadHash() != null
                && webhookEventRepository.existsBySourceAndPayloadHash(event.source(), event.payloadHash())) {
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(event.source());
        entity.setEventId(event.eventId());
        entity.setStatus(event.status());
        entity.setPayload(event.payload());
        entity.setPayloadHash(event.payloadHash());
        entity.setReceivedAt(event.receivedAt());

        try {
            webhookEventRepository.save(entity);
        } catch (DataIntegrityViolationException ignored) {
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        webhookDispatcher.dispatch(event);
        return new WebhookIngressResult(acceptedMessage);
    }

    private String extractEventType(NormalizedWebhookEvent event) {
        if (event == null || event.payload() == null || event.payload().get("eventType") == null) {
            return "";
        }
        return event.payload().get("eventType").asText("");
    }
}
