package com.fuba.automation_engine.service.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.exception.person.InvalidPersonFeedQueryException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Opaque keyset cursor for the persons feed. Encodes {@code (updatedAt, id)}
 * which is the ordering used by the feed query (<code>ORDER BY updated_at DESC, id DESC</code>).
 *
 * <p>Mirrors {@code WebhookFeedCursorCodec} — separate class because the
 * underlying key pair is different ({@code receivedAt} for webhooks vs
 * {@code updatedAt} for persons) and the exception type is domain-scoped.
 */
@Component
public class PersonFeedCursorCodec {

    private final ObjectMapper objectMapper;

    public PersonFeedCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(OffsetDateTime updatedAt, Long id) {
        if (updatedAt == null || id == null) {
            throw new InvalidPersonFeedQueryException("Cursor requires updatedAt and id");
        }

        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("updatedAt", updatedAt.toString());
            node.put("id", id);
            byte[] encodedBytes = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encode(objectMapper.writeValueAsBytes(node));
            return new String(encodedBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new InvalidPersonFeedQueryException("Unable to encode cursor");
        }
    }

    public Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode node = objectMapper.readTree(decoded);
            String updatedAtValue = node.path("updatedAt").asText(null);
            JsonNode idNode = node.get("id");
            if (updatedAtValue == null || idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                throw new InvalidPersonFeedQueryException("Invalid cursor");
            }
            return new Cursor(OffsetDateTime.parse(updatedAtValue), idNode.longValue());
        } catch (InvalidPersonFeedQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidPersonFeedQueryException("Invalid cursor");
        }
    }

    public record Cursor(OffsetDateTime updatedAt, Long id) {
    }
}
