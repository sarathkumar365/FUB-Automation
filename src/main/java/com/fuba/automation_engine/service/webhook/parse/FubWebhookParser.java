package com.fuba.automation_engine.service.webhook.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FubWebhookParser implements WebhookParser {
    // Extension note:
    // To add another webhook source, create a new parser implementing WebhookParser
    // and return true from supports(source) for that source. Keep normalization output
    // aligned with NormalizedWebhookEvent so controller/service flow stays unchanged.

    private static final String EVENT_ID = "eventId";
    private static final String EVENT = "event";
    private static final String RESOURCE_IDS = "resourceIds";
    private static final String URI = "uri";

    private final ObjectMapper objectMapper;

    public FubWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(WebhookSource source) {
        return WebhookSource.FUB == source;
    }

    @Override
    public NormalizedWebhookEvent parse(String rawBody, Map<String, String> headers) {
        JsonNode json;
        try {
            json = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new MalformedWebhookPayloadException("Webhook payload must be valid JSON");
        }

        JsonNode eventNode = json.get(EVENT);
        JsonNode resourceIdsNode = json.get(RESOURCE_IDS);
        if (eventNode == null || eventNode.asText().isBlank()) {
            throw new MalformedWebhookPayloadException("Webhook payload missing 'event'");
        }
        if (resourceIdsNode == null || !resourceIdsNode.isArray()) {
            throw new MalformedWebhookPayloadException("Webhook payload missing 'resourceIds' array");
        }

        String eventId = json.hasNonNull(EVENT_ID) ? json.get(EVENT_ID).asText() : null;
        String payloadHash = calculatePayloadHash(rawBody == null ? "" : rawBody);

        ObjectNode payloadNode = objectMapper.createObjectNode();
        payloadNode.put("eventType", eventNode.asText());

        ArrayNode resourceIds = payloadNode.putArray("resourceIds");
        resourceIdsNode.forEach(resourceIds::add);

        if (json.has(URI) && !json.get(URI).isNull()) {
            payloadNode.put("uri", json.get(URI).asText());
        } else {
            payloadNode.putNull("uri");
        }

        ObjectNode selectedHeaders = payloadNode.putObject("headers");
        addHeader(selectedHeaders, headers, "FUB-Signature");
        addHeader(selectedHeaders, headers, "User-Agent");
        addHeader(selectedHeaders, headers, "Content-Type");

        payloadNode.put("rawBody", rawBody);

        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                eventId,
                WebhookEventStatus.RECEIVED,
                payloadNode,
                OffsetDateTime.now(),
                payloadHash);
    }

    private void addHeader(ObjectNode selectedHeaders, Map<String, String> headers, String name) {
        headers.forEach((key, value) -> {
            if (key != null && key.equalsIgnoreCase(name)) {
                selectedHeaders.put(name, value);
            }
        });
    }

    private String calculatePayloadHash(String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawBody.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate payload hash", ex);
        }
    }
}
