package com.fuba.automation_engine.service.webhook;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookSignatureException;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.exception.webhook.UnsupportedWebhookSourceException;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.dispatch.WebhookDispatcher;
import com.fuba.automation_engine.service.webhook.live.WebhookLiveFeedPublisher;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookIngressResult;
import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.webhook.parse.WebhookParser;
import com.fuba.automation_engine.service.webhook.security.WebhookSignatureVerifier;
import com.fuba.automation_engine.service.webhook.support.EventSupportResolution;
import com.fuba.automation_engine.service.webhook.support.WebhookEventSupportResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WebhookIngressService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngressService.class);
    private static final String EVENT_TYPE_UNKNOWN = "UNKNOWN";

    private final List<WebhookSignatureVerifier> signatureVerifiers;
    private final List<WebhookParser> parsers;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookEventSupportResolver webhookEventSupportResolver;
    private final WebhookDispatcher webhookDispatcher;
    private final WebhookLiveFeedPublisher webhookLiveFeedPublisher;
    private final WebhookProperties webhookProperties;

    public WebhookIngressService(
            List<WebhookSignatureVerifier> signatureVerifiers,
            List<WebhookParser> parsers,
            WebhookEventRepository webhookEventRepository,
            WebhookEventSupportResolver webhookEventSupportResolver,
            WebhookDispatcher webhookDispatcher,
            WebhookLiveFeedPublisher webhookLiveFeedPublisher,
            WebhookProperties webhookProperties) {
        this.signatureVerifiers = signatureVerifiers;
        this.parsers = parsers;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookEventSupportResolver = webhookEventSupportResolver;
        this.webhookDispatcher = webhookDispatcher;
        this.webhookLiveFeedPublisher = webhookLiveFeedPublisher;
        this.webhookProperties = webhookProperties;
    }

    public WebhookIngressResult ingest(String sourcePath, String rawBody, Map<String, String> headers) {
        WebhookSource source = WebhookSource.fromPathValue(sourcePath);
        if (source == null) {
            log.warn("Unsupported webhook source received sourcePath={}", sourcePath);
            throw new UnsupportedWebhookSourceException("Unsupported webhook source: " + sourcePath);
        }

        if (source == WebhookSource.FUB && !webhookProperties.getSources().getFub().isEnabled()) {
            log.warn("Webhook source disabled source={}", sourcePath);
            throw new UnsupportedWebhookSourceException("Webhook source not enabled: " + sourcePath);
        }

        if (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > webhookProperties.getMaxBodyBytes()) {
            log.warn("Webhook payload too large source={} bytes={}", sourcePath, rawBody.getBytes(StandardCharsets.UTF_8).length);
            throw new MalformedWebhookPayloadException("Payload exceeds max allowed body size");
        }

        WebhookSignatureVerifier verifier = signatureVerifiers.stream()
                .filter(it -> it.supports(source))
                .findFirst()
                .orElseThrow(() -> new UnsupportedWebhookSourceException("No signature verifier configured for source: " + source));

        if (!verifier.verify(rawBody, headers)) {
            log.warn("Webhook signature check failed source={}", source);
            throw new InvalidWebhookSignatureException("Webhook signature validation failed");
        }

        WebhookParser parser = parsers.stream()
                .filter(it -> it.supports(source))
                .findFirst()
                .orElseThrow(() -> new UnsupportedWebhookSourceException("No parser configured for source: " + source));
        // parse -> resolve catalog -> persist -> live feed -> dispatch only if SUPPORTED.
        NormalizedWebhookEvent event = parser.parse(rawBody, headers);
        String eventType = extractEventType(event);
        EventSupportResolution resolution = webhookEventSupportResolver.resolve(event.sourceSystem(), eventType);
        log.info(
                "Webhook normalized source={} eventId={} eventType={} supportState={} normalizedDomain={} normalizedAction={}",
                event.sourceSystem(),
                event.eventId(),
                eventType,
                resolution.supportState(),
                resolution.normalizedDomain(),
                resolution.normalizedAction());
        String acceptedMessage = resolution.supportState() == EventSupportState.SUPPORTED
                ? "Webhook accepted for async processing"
                : "Event type not supported yet: " + eventType;

        if (event.eventId() != null && !event.eventId().isBlank()
                && webhookEventRepository.existsBySourceAndEventId(event.sourceSystem(), event.eventId())) {
            log.info("Duplicate webhook ignored by eventId source={} eventId={}", event.sourceSystem(), event.eventId());
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        if ((event.eventId() == null || event.eventId().isBlank())
                && event.payloadHash() != null
                && webhookEventRepository.existsBySourceAndPayloadHash(event.sourceSystem(), event.payloadHash())) {
            log.info("Duplicate webhook ignored by payloadHash source={} payloadHash={}", event.sourceSystem(), event.payloadHash());
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(event.sourceSystem());
        entity.setEventId(event.eventId());
        entity.setEventType(eventType);
        entity.setCatalogState(resolution.supportState());
        entity.setNormalizedDomain(resolution.normalizedDomain());
        entity.setNormalizedAction(resolution.normalizedAction());
        entity.setSourceLeadId(event.sourceLeadId());
        entity.setStatus(event.status());
        entity.setPayload(event.payload());
        entity.setPayloadHash(event.payloadHash());
        entity.setReceivedAt(event.receivedAt());

        WebhookEventEntity savedEntity;
        try {
            savedEntity = webhookEventRepository.save(entity);
        } catch (DataIntegrityViolationException ignored) {
            // TODO: Narrow duplicate handling to unique-key violations only.
            // Other integrity failures (for example, event_type column length mismatch)
            // should not be classified as duplicates because they can hide data loss.
            log.info("Duplicate webhook ignored during save source={} eventId={}", event.sourceSystem(), event.eventId());
            return new WebhookIngressResult("Duplicate webhook ignored");
        }

        publishLiveFeed(savedEntity);

        if (resolution.supportState() == EventSupportState.SUPPORTED) {
            log.info("Dispatching webhook event asynchronously source={} eventId={}", event.sourceSystem(), event.eventId());
            webhookDispatcher.dispatch(event);
        } else {
            log.info(
                    "Skipping dispatch for non-supported event source={} eventId={} eventType={} supportState={}",
                    event.sourceSystem(),
                    event.eventId(),
                    eventType,
                    resolution.supportState());
        }
        return new WebhookIngressResult(acceptedMessage);
    }

    private String extractEventType(NormalizedWebhookEvent event) {
        if (event == null) {
            return EVENT_TYPE_UNKNOWN;
        }
        String sourceEventType = event.sourceEventType();
        if (sourceEventType != null && !sourceEventType.isBlank()) {
            return sourceEventType.trim();
        }
        if (event.payload() == null || event.payload().get("eventType") == null) {
            return EVENT_TYPE_UNKNOWN;
        }
        String fallbackPayloadEventType = event.payload().get("eventType").asText("").trim();
        return fallbackPayloadEventType.isBlank() ? EVENT_TYPE_UNKNOWN : fallbackPayloadEventType;
    }

    private void publishLiveFeed(WebhookEventEntity entity) {
        WebhookLiveFeedEvent liveFeedEvent = new WebhookLiveFeedEvent(
                entity.getId(),
                entity.getEventId(),
                entity.getSource(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getReceivedAt());

        try {
            webhookLiveFeedPublisher.publish(liveFeedEvent);
        } catch (RuntimeException ex) {
            log.warn(
                    "Live feed publish failed but webhook ingest will continue id={} eventId={} source={}",
                    entity.getId(),
                    entity.getEventId(),
                    entity.getSource(),
                    ex);
        }
    }
}
