package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookFeedQueryException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class WebhookFeedCursorCodec {

    private final ObjectMapper objectMapper;

    public WebhookFeedCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(OffsetDateTime receivedAt, Long id) {
        // Cursor is a keyset bookmark for "continue after last seen row" pagination.
        // Example: if page 1 ends at (receivedAt=10:04, id=104), the next query requests
        // rows older than that boundary (or same timestamp with lower id).
        if (receivedAt == null || id == null) {
            throw new InvalidWebhookFeedQueryException("Cursor requires receivedAt and id");
        }

        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("receivedAt", receivedAt.toString());
            node.put("id", id);
            byte[] encodedBytes = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encode(objectMapper.writeValueAsBytes(node));
            return new String(encodedBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new InvalidWebhookFeedQueryException("Unable to encode cursor");
        }
    }

    public Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode node = objectMapper.readTree(decoded);
            String receivedAtValue = node.path("receivedAt").asText(null);
            JsonNode idNode = node.get("id");
            if (receivedAtValue == null || idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                throw new InvalidWebhookFeedQueryException("Invalid cursor");
            }
            return new Cursor(OffsetDateTime.parse(receivedAtValue), idNode.longValue());
        } catch (InvalidWebhookFeedQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidWebhookFeedQueryException("Invalid cursor");
        }
    }

    public record Cursor(OffsetDateTime receivedAt, Long id) {
    }
}
