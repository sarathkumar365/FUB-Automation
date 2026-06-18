package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.service.event.DomainEvent;
import com.fuba.automation_engine.service.person.PersonSnapshotResolver;
import com.fuba.automation_engine.service.workflow.expression.DomainEventScopeBuilder;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.JsonataExpressionEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // --- anyOf ---

    @Test
    void anyOfFiresOnCreatedAssignedLead() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD", "assignedUserId", 10));
        assertTrue(trigger.matches(createdEvent(), mvpAnyOf()));
    }

    @Test
    void anyOfFiresOnReassignment() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        assertTrue(trigger.matches(assignmentEvent(11, false), mvpAnyOf()));
    }

    @Test
    void anyOfDoesNotFireWhenNoEntrySubscribesToKind() {
        DomainEvent note = new DomainEvent(3L, "note.created", "FUB", null, "person", "20300", mapper.createObjectNode());
        assertFalse(trigger.matches(note, mvpAnyOf()));
    }

    @Test
    void anyOfDoesNotFireOnStateChangeWithoutAssignment() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        ObjectNode payload = mapper.createObjectNode();
        payload.set("changed_fields", mapper.valueToTree(List.of("stage")));
        payload.set("previous", mapper.createObjectNode().put("stage", "Lead"));
        payload.set("current", mapper.createObjectNode().put("stage", "Customer"));
        DomainEvent event = new DomainEvent(6L, "person.state_changed", "FUB", null, "person", "20235", payload);
        assertFalse(trigger.matches(event, mvpAnyOf()));
    }

    @Test
    void anyOfBadEntryFilterDoesNotVetoSibling() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        Map<String, Object> trigger2 = Map.of("anyOf", List.of(
                Map.of("on", "person.state_changed", "filter", "this is ( not valid"),
                Map.of("on", "person.state_changed", "filter", "person.kind = 'LEAD'")));
        assertTrue(trigger.matches(assignmentEvent(11, false), trigger2));
    }

    @Test
    void anyOfThrowingEntryFilterDoesNotVetoSibling() {
        ExpressionEvaluator throwingEvaluator = mock(ExpressionEvaluator.class);
        when(throwingEvaluator.evaluatePredicate(eq("BOOM"), any())).thenThrow(new RuntimeException("boom"));
        when(throwingEvaluator.evaluatePredicate(eq("OK"), any())).thenReturn(Boolean.TRUE);
        DomainEventTriggerType local = new DomainEventTriggerType(
                personResolver, new DomainEventScopeBuilder(mapper), throwingEvaluator);
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        Map<String, Object> trigger2 = Map.of("anyOf", List.of(
                Map.of("on", "person.state_changed", "filter", "BOOM"),
                Map.of("on", "person.state_changed", "filter", "OK")));
        assertTrue(local.matches(assignmentEvent(11, false), trigger2));
    }

    @Test
    void anyOfBuildsScopeOnceAcrossEntries() {
        when(personResolver.resolve(anyString())).thenReturn(Map.of("kind", "LEAD"));
        // duplicate kinds (validation-rejected) force two filter evals — scope must still build once
        Map<String, Object> trigger2 = Map.of("anyOf", List.of(
                Map.of("on", "person.state_changed", "filter", "person.kind = 'AGENT'"),
                Map.of("on", "person.state_changed", "filter", "person.kind = 'LEAD'")));
        assertTrue(trigger.matches(assignmentEvent(11, false), trigger2));
        verify(personResolver, times(1)).resolve(anyString());
    }

    private Map<String, Object> mvpAnyOf() {
        return Map.of("anyOf", List.of(
                Map.of("on", "person.created",
                        "filter", "person.kind = 'LEAD' and $boolean(person.assignedUserId)"),
                Map.of("on", "person.state_changed",
                        "filter", "person.kind = 'LEAD' and change.assignedUserId.changed "
                                + "and $boolean(change.assignedUserId.new)")));
    }

    private DomainEvent createdEvent() {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("current", mapper.createObjectNode().put("kind", "LEAD").put("assignedUserId", 10));
        return new DomainEvent(2L, "person.created", "FUB", null, "person", "20300", payload);
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
