package com.fuba.automation_engine.service.workflow.expression;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.service.event.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the JSONata variable bag a domain-event trigger filter evaluates
 * against. Single source of the domain-event scope shape (Phase 4) — the
 * step-time scope (4d) reuses it so trigger-time and step-time can't drift.
 *
 * <p>{@code event.origin} carries the engine-write provenance, read from the
 * top-level {@code source} annotation Phase 3 stamps on engine-caused events.
 * It is <b>always present</b> — {@code "ENGINE"} when the engine caused the
 * event, else {@code "EXTERNAL"} — never absent. (An absent value would make
 * the standard {@code event.origin != 'ENGINE'} predicate evaluate to
 * <i>undefined</i> in JSONata rather than true, silently dropping real
 * external events.) It lives under {@code event}, not {@code change}, because
 * {@code source} is also a diffable person field (the FUB lead source) —
 * keeping provenance out of {@code change.*} lets {@code change.source} mean
 * the field delta and {@code event.origin} mean "who caused this event".
 */
@Component
public class DomainEventScopeBuilder {

    private static final String EVENT_KIND_PERSON_STATE_CHANGED = "person.state_changed";
    private static final String ANNOTATION_SOURCE_KEY = "source";
    private static final String ORIGIN_ENGINE = "ENGINE";
    private static final String ORIGIN_EXTERNAL = "EXTERNAL";

    private final ObjectMapper objectMapper;

    public DomainEventScopeBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> build(DomainEvent event, Map<String, Object> person) {
        Map<String, Object> scope = new LinkedHashMap<>();
        JsonNode payload = event.payload();

        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("kind", event.eventKind());
        if (event.id() != null) {
            eventMap.put("id", event.id());
        }
        if (event.entityType() != null) {
            eventMap.put("entityType", event.entityType());
        }
        if (event.entityId() != null) {
            eventMap.put("entityId", event.entityId());
        }
        eventMap.put("payload", toMap(payload));
        eventMap.put("origin", originOf(payload));
        scope.put("event", eventMap);

        // current.* — full snapshot for person.created, changed-only for person.state_changed
        if (payload != null && payload.hasNonNull("current")) {
            scope.put("current", toMap(payload.get("current")));
        }

        // change.* — per-field deltas; person.state_changed only
        if (EVENT_KIND_PERSON_STATE_CHANGED.equals(event.eventKind()) && payload != null) {
            scope.put("change", buildChange(payload));
        }

        scope.put("person", person != null ? person : Map.of());
        return scope;
    }

    private String originOf(JsonNode payload) {
        boolean engine = payload != null
                && payload.hasNonNull(ANNOTATION_SOURCE_KEY)
                && ORIGIN_ENGINE.equals(payload.get(ANNOTATION_SOURCE_KEY).asText());
        return engine ? ORIGIN_ENGINE : ORIGIN_EXTERNAL;
    }

    private Map<String, Object> buildChange(JsonNode payload) {
        Map<String, Object> change = new LinkedHashMap<>();
        JsonNode changedFields = payload.get("changed_fields");
        JsonNode previous = payload.get("previous");
        JsonNode current = payload.get("current");
        if (changedFields != null && changedFields.isArray()) {
            for (JsonNode fieldNode : changedFields) {
                String field = fieldNode.asText();
                Map<String, Object> delta = new LinkedHashMap<>();
                delta.put("changed", true);
                delta.put("old", value(previous, field));
                delta.put("new", value(current, field));
                change.put(field, delta);
            }
        }
        return change;
    }

    private Object value(JsonNode container, String field) {
        if (container == null || !container.hasNonNull(field)) {
            return null;
        }
        return objectMapper.convertValue(container.get(field), Object.class);
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        Map<String, Object> map = objectMapper.convertValue(node, new TypeReference<>() {});
        return map != null ? map : Map.of();
    }
}
