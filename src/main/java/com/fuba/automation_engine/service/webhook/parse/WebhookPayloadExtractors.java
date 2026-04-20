package com.fuba.automation_engine.service.webhook.parse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for reading the {@code resourceIds} array from webhook payloads.
 * Both the parser (working on the raw provider JSON) and the processor (working on the
 * normalized payload JsonNode) need the same extraction semantics, so they live here.
 */
public final class WebhookPayloadExtractors {

    private WebhookPayloadExtractors() {
    }

    /** Returns resourceIds convertible to long; null / non-array inputs yield an empty list. */
    public static List<Long> extractResourceIdsAsLongs(JsonNode resourceIdsNode) {
        List<Long> result = new ArrayList<>();
        if (resourceIdsNode == null || !resourceIdsNode.isArray()) {
            return result;
        }
        for (JsonNode node : resourceIdsNode) {
            if (node != null && node.canConvertToLong()) {
                result.add(node.asLong());
            }
        }
        return result;
    }

    /** Returns the first resourceId rendered as a trimmed string, or null if missing/blank. */
    public static String firstResourceIdAsString(JsonNode resourceIdsNode) {
        if (resourceIdsNode == null || !resourceIdsNode.isArray() || resourceIdsNode.isEmpty()) {
            return null;
        }
        JsonNode first = resourceIdsNode.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        String value = first.asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
