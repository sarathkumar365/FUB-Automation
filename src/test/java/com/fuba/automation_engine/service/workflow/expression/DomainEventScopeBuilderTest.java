package com.fuba.automation_engine.service.workflow.expression;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.service.event.DomainEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventScopeBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DomainEventScopeBuilder builder = new DomainEventScopeBuilder(mapper);

    @Test
    @SuppressWarnings("unchecked")
    void stateChangedExposesEventChangeCurrentPerson() {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(java.util.List.of("assignedUserId")));
        payload.set("previous", mapper.createObjectNode().put("assignedUserId", 10));
        payload.set("current", mapper.createObjectNode().put("assignedUserId", 11));

        DomainEvent event = new DomainEvent(99L, "person.state_changed", "FUB", 7L, "person", "20235", payload);

        Map<String, Object> scope = builder.build(event, Map.of("kind", "LEAD"));

        Map<String, Object> ev = (Map<String, Object>) scope.get("event");
        assertEquals(99L, ev.get("id"));
        assertEquals("person.state_changed", ev.get("kind"));
        assertEquals("person", ev.get("entityType"));
        assertEquals("20235", ev.get("entityId"));
        assertEquals("EXTERNAL", ev.get("origin")); // no engine annotation

        Map<String, Object> change = (Map<String, Object>) scope.get("change");
        Map<String, Object> delta = (Map<String, Object>) change.get("assignedUserId");
        assertEquals(true, delta.get("changed"));
        assertEquals(10, delta.get("old"));
        assertEquals(11, delta.get("new"));

        Map<String, Object> current = (Map<String, Object>) scope.get("current");
        assertEquals(11, current.get("assignedUserId"));

        assertEquals(Map.of("kind", "LEAD"), scope.get("person"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void engineAnnotationSurfacesAsEventOriginEngine() {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(java.util.List.of("assignedUserId")));
        payload.set("previous", mapper.createObjectNode().put("assignedUserId", 10));
        payload.set("current", mapper.createObjectNode().put("assignedUserId", 11));
        payload.put("source", "ENGINE"); // Phase 3 engine-write annotation

        DomainEvent event = new DomainEvent(1L, "person.state_changed", "FUB", null, "person", "20235", payload);

        Map<String, Object> ev = (Map<String, Object>) builder.build(event, Map.of()).get("event");
        assertEquals("ENGINE", ev.get("origin"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void changeSourceIsTheFieldDeltaNotTheAnnotation() {
        // The lead-source field changed AND the event is engine-annotated:
        // change.source must be the field delta; event.origin carries provenance.
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(java.util.List.of("source")));
        payload.set("previous", mapper.createObjectNode().put("source", "Zillow"));
        payload.set("current", mapper.createObjectNode().put("source", "Website"));
        payload.put("source", "ENGINE");

        DomainEvent event = new DomainEvent(2L, "person.state_changed", "FUB", null, "person", "1", payload);
        Map<String, Object> scope = builder.build(event, Map.of());

        Map<String, Object> change = (Map<String, Object>) scope.get("change");
        Map<String, Object> sourceDelta = (Map<String, Object>) change.get("source");
        assertEquals(true, sourceDelta.get("changed"));
        assertEquals("Zillow", sourceDelta.get("old"));
        assertEquals("Website", sourceDelta.get("new"));

        Map<String, Object> ev = (Map<String, Object>) scope.get("event");
        assertEquals("ENGINE", ev.get("origin")); // provenance is separate from the field
    }

    @Test
    @SuppressWarnings("unchecked")
    void createdExposesFullCurrentAndNoChange() {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode snapshot = mapper.createObjectNode().put("stage", "Lead").put("assignedUserId", 5);
        payload.set("current", snapshot);

        DomainEvent event = new DomainEvent(3L, "person.created", "FUB", 8L, "person", "42", payload);
        Map<String, Object> scope = builder.build(event, Map.of("kind", "LEAD"));

        assertNull(scope.get("change")); // change.* only for state_changed
        Map<String, Object> current = (Map<String, Object>) scope.get("current");
        assertEquals("Lead", current.get("stage"));
        assertEquals(5, current.get("assignedUserId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unchangedFieldIsAbsentFromChange() {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(java.util.List.of("stage")));
        payload.set("previous", mapper.createObjectNode().put("stage", "Lead"));
        payload.set("current", mapper.createObjectNode().put("stage", "Customer"));

        DomainEvent event = new DomainEvent(4L, "person.state_changed", "FUB", null, "person", "1", payload);
        Map<String, Object> change = (Map<String, Object>) builder.build(event, Map.of()).get("change");

        assertTrue(change.containsKey("stage"));
        assertFalse(change.containsKey("assignedUserId")); // didn't change → absent → JSONata falsy
    }
}
