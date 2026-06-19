package com.flux.service.workflow.trigger;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedTriggerTest {

    @Test
    void flatTriggerBecomesSingleEntry() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("on", "person.created", "filter", "person.kind = 'LEAD'"));

        assertFalse(parsed.isAnyOf());
        assertEquals(List.of(new TriggerEntry("person.created", "person.kind = 'LEAD'")), parsed.entries());
        assertTrue(parsed.shapeErrors().isEmpty());
        assertTrue(parsed.subscribesTo("person.created"));
        assertFalse(parsed.subscribesTo("person.state_changed"));
    }

    @Test
    void flatTriggerWithoutFilterHasNullFilter() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("on", "person.created"));

        assertEquals(List.of(new TriggerEntry("person.created", null)), parsed.entries());
        assertTrue(parsed.shapeErrors().isEmpty());
    }

    @Test
    void anyOfBecomesMultipleEntries() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", List.of(
                Map.of("on", "person.created", "filter", "a"),
                Map.of("on", "person.state_changed", "filter", "b"))));

        assertTrue(parsed.isAnyOf());
        assertEquals(List.of(
                new TriggerEntry("person.created", "a"),
                new TriggerEntry("person.state_changed", "b")), parsed.entries());
        assertTrue(parsed.shapeErrors().isEmpty());
        assertTrue(parsed.subscribesTo("person.created"));
        assertTrue(parsed.subscribesTo("person.state_changed"));
        assertFalse(parsed.subscribesTo("call.created"));
    }

    @Test
    void rejectsBothOnAndAnyOf() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of(
                "on", "person.created",
                "anyOf", List.of(Map.of("on", "person.created"))));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsNeitherOnNorAnyOf() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("filter", "x"));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsEmptyAnyOf() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", List.of()));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsAnyOfThatIsNotAnArray() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", "nope"));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsAnyOfEntryMissingOn() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", List.of(Map.of("filter", "x"))));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsDuplicateKindsAcrossEntries() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", List.of(
                Map.of("on", "person.created", "filter", "a"),
                Map.of("on", "person.created", "filter", "b"))));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void rejectsReactToEngineEventsInsideEntry() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of("anyOf", List.of(
                Map.of("on", "person.created", "reactToEngineEvents", false))));

        assertFalse(parsed.shapeErrors().isEmpty());
    }

    @Test
    void allowsTopLevelReactToEngineEvents() {
        ParsedTrigger parsed = ParsedTrigger.from(Map.of(
                "anyOf", List.of(Map.of("on", "person.created")),
                "reactToEngineEvents", true));

        assertTrue(parsed.shapeErrors().isEmpty());
    }
}
