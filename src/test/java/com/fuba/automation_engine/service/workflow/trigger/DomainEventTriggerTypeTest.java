package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.service.event.DomainEvent;
import com.fuba.automation_engine.service.person.PersonSnapshotResolver;
import com.fuba.automation_engine.service.workflow.expression.DomainEventScopeBuilder;
import com.fuba.automation_engine.service.workflow.expression.JsonataExpressionEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainEventTriggerTypeTest {

    private static final String FILTER =
            "person.kind = 'LEAD' and change.assignedUserId.changed and event.origin != 'ENGINE'";

    private final ObjectMapper mapper = new ObjectMapper();
    private final PersonSnapshotResolver personResolver = mock(PersonSnapshotResolver.class);
    private final DomainEventTriggerType trigger = new DomainEventTriggerType(
            personResolver,
            new DomainEventScopeBuilder(mapper),
            new JsonataExpressionEvaluator());

    @Test
    void doesNotMatchWhenEventKindDiffersFromOn() {
        DomainEvent event = assignmentEvent(11, false);
        assertFalse(trigger.matches(event, Map.of("on", "person.created")));
    }

    @Test
    void matchesOnKindAloneWhenNoFilter() {
        DomainEvent event = assignmentEvent(11, false);
        assertTrue(trigger.matches(event, Map.of("on", "person.state_changed")));
    }

    @Test
    void productionFilterFiresOnRealLeadAssignment() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        DomainEvent event = assignmentEvent(11, false); // not engine-caused
        assertTrue(trigger.matches(event, Map.of("on", "person.state_changed", "filter", FILTER)));
    }

    @Test
    void productionFilterSuppressesEngineEcho() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        DomainEvent event = assignmentEvent(11, true); // engine-caused → origin ENGINE
        assertFalse(trigger.matches(event, Map.of("on", "person.state_changed", "filter", FILTER)));
    }

    @Test
    void productionFilterRejectsNonLead() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "AGENT"));
        DomainEvent event = assignmentEvent(11, false);
        assertFalse(trigger.matches(event, Map.of("on", "person.state_changed", "filter", FILTER)));
    }

    @Test
    void productionFilterRejectsWhenWatchedFieldDidNotChange() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        // Only stage changed; assignedUserId absent from change.* → change.assignedUserId.changed falsy.
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(List.of("stage")));
        payload.set("previous", mapper.createObjectNode().put("stage", "Lead"));
        payload.set("current", mapper.createObjectNode().put("stage", "Customer"));
        DomainEvent event = new DomainEvent(5L, "person.state_changed", "FUB", null, "person", "20235", payload);

        assertFalse(trigger.matches(event, Map.of("on", "person.state_changed", "filter", FILTER)));
    }

    @Test
    void extractsPersonEntityRef() {
        DomainEvent event = assignmentEvent(11, false);
        List<EntityRef> refs = trigger.extractEntities(event);
        assertEquals(List.of(new EntityRef("person", "20235")), refs);
    }

    /** A person.state_changed where assignedUserId 10 → given, optionally engine-annotated. */
    private DomainEvent assignmentEvent(int newUserId, boolean engineCaused) {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(List.of("assignedUserId")));
        payload.set("previous", mapper.createObjectNode().put("assignedUserId", 10));
        payload.set("current", mapper.createObjectNode().put("assignedUserId", newUserId));
        if (engineCaused) {
            payload.put("source", "ENGINE");
        }
        return new DomainEvent(1L, "person.state_changed", "FUB", null, "person", "20235", payload);
    }
}
