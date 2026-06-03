package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.workflow.expression.JsonataExpressionEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventTriggerValidatorTest {

    private final DomainEventTriggerValidator validator =
            new DomainEventTriggerValidator(new JsonataExpressionEvaluator());

    private boolean hasError(List<String> errors, String needle) {
        return errors.stream().anyMatch(e -> e.contains(needle));
    }

    @Test
    void productionTriggerIsValid() {
        // No echo predicate — engine-echo exclusion is platform-enforced (RD-006).
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "person.kind = 'LEAD' and change.assignedUserId.changed"));
        assertTrue(errors.isEmpty(), () -> "expected valid, got " + errors);
    }

    @Test
    void kindOnlyTriggerIsValid() {
        assertTrue(validator.validate(Map.of("on", "person.created")).isEmpty());
    }

    @Test
    void unknownEventKindRefused() {
        List<String> errors = validator.validate(Map.of("on", "person.exploded"));
        assertTrue(hasError(errors, "unknown event kind"));
    }

    @Test
    void missingOnRefused() {
        assertTrue(hasError(validator.validate(Map.of("filter", "person.kind = 'LEAD'")),
                "trigger.on is required"));
    }

    @Test
    void uncapturedChangeFieldRefused() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "change.madeUpField.changed"));
        assertTrue(hasError(errors, "change.madeUpField"));
    }

    @Test
    void uncapturedPersonFieldRefused() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "person.madeUpField = 'x' and change.stage.changed"));
        assertTrue(hasError(errors, "person.madeUpField"));
    }

    @Test
    void eventOriginIsAlwaysValid() {
        // event.* is metadata — never field-checked.
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "change.assignedUserId.changed and event.origin = 'ENGINE'"));
        assertTrue(errors.isEmpty(), () -> "expected valid, got " + errors);
    }

    @Test
    void changeStarRejectedOnNonStateChangedKind() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.created",
                "filter", "change.stage.changed"));
        assertTrue(hasError(errors, "change.* is only available for person.state_changed"));
    }

    @Test
    void currentStarRejectedOnAppendKind() {
        List<String> errors = validator.validate(Map.of(
                "on", "call.created",
                "filter", "current.stage = 'Lead'"));
        assertTrue(hasError(errors, "current.* is only available"));
    }

    @Test
    void invalidJsonataFilterRefused() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "(person.kind = 'LEAD'")); // unbalanced paren
        assertTrue(hasError(errors, "not a valid JSONata expression"));
    }

    @Test
    void nonBooleanReactToEngineEventsRefused() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "reactToEngineEvents", "yes"));
        assertTrue(hasError(errors, "reactToEngineEvents must be a boolean"));
    }

    @Test
    void booleanReactToEngineEventsAccepted() {
        List<String> errors = validator.validate(Map.of(
                "on", "person.state_changed",
                "filter", "change.assignedUserId.changed",
                "reactToEngineEvents", true));
        assertTrue(errors.isEmpty(), () -> "expected valid, got " + errors);
    }

    @Test
    void appendKindPayloadReferenceNotFieldChecked() {
        // event.payload.* on an append kind is unvalidated (#30) — must not error.
        List<String> errors = validator.validate(Map.of(
                "on", "call.created",
                "filter", "event.payload.durationSec > 60"));
        assertTrue(errors.isEmpty(), () -> "expected valid (unvalidated payload), got " + errors);
        assertFalse(hasError(errors, "durationSec"));
    }
}
