package com.fuba.automation_engine.service.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.exception.policy.InvalidPolicyExecutionQueryException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class PolicyExecutionCursorCodec {

    private final ObjectMapper objectMapper;

    public PolicyExecutionCursorCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(OffsetDateTime createdAt, Long id) {
        if (createdAt == null || id == null) {
            throw new InvalidPolicyExecutionQueryException("Cursor requires createdAt and id");
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("createdAt", createdAt.toString());
            node.put("id", id);
            byte[] encoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encode(objectMapper.writeValueAsBytes(node));
            return new String(encoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new InvalidPolicyExecutionQueryException("Unable to encode cursor");
        }
    }

    public Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(null, null);
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode node = objectMapper.readTree(decoded);
            String createdAtValue = node.path("createdAt").asText(null);
            JsonNode idNode = node.get("id");
            if (createdAtValue == null || idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                throw new InvalidPolicyExecutionQueryException("Invalid cursor");
            }
            return new Cursor(OffsetDateTime.parse(createdAtValue), idNode.longValue());
        } catch (InvalidPolicyExecutionQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidPolicyExecutionQueryException("Invalid cursor");
        }
    }

    public record Cursor(OffsetDateTime createdAt, Long id) {
    }
}
