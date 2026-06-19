package com.flux.service.workflow.trigger;

import com.flux.service.person.PersonDiffComputer;
import com.flux.service.person.PersonUpsertService;
import com.flux.service.workflow.expression.ExpressionEvaluator;
import com.flux.service.workflow.spi.TriggerValidator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Save-time validation for domain-event triggers — the flat
 * {@code { "on": <kind>, "filter": <JSONata>, "reactToEngineEvents": <bool> }}
 * shape and the {@code { "anyOf": [ { "on": ..., "filter": ... }, ... ] }} shape.
 * {@link ParsedTrigger} checks the shape; each entry's kind and filter are then
 * validated against that kind.
 *
 * <p>Closes the silent-no-fire bug: a filter that references a field the event
 * kind does not carry can never be true, so the workflow looks healthy but
 * never runs. We refuse such references at save time. Person field references
 * are checked against the captured snapshot ({@code person.*} / {@code current.*})
 * and the diffable set ({@code change.*}); {@code event.*} is always valid
 * metadata.
 *
 * <p>Engine-echo exclusion is platform-enforced and safe-by-default
 * ({@code RD-006}), so there is no echo predicate to validate — the only
 * top-level check on {@code reactToEngineEvents} is that it is a boolean.
 *
 * <p>Append kinds ({@code call.created}, {@code note.*}) have no declared field
 * schema (raw FUB payload), so their {@code event.payload.*} references are not
 * field-checked — a documented gap (known-issue #30) until a consumer exists.
 */
@Component
public class DomainEventTriggerValidator implements TriggerValidator {

    private static final String PERSON_CREATED = PersonUpsertService.EVENT_KIND_PERSON_CREATED;
    private static final String PERSON_STATE_CHANGED = PersonUpsertService.EVENT_KIND_PERSON_STATE_CHANGED;

    /** Known event kinds a trigger may subscribe to. Note kinds have no constants today. */
    private static final Set<String> KNOWN_EVENT_KINDS = Set.of(
            PERSON_CREATED, PERSON_STATE_CHANGED,
            "call.created", "note.created", "note.updated", "note.deleted");

    private static final Set<String> CURRENT_KINDS = Set.of(PERSON_CREATED, PERSON_STATE_CHANGED);

    /** Event kinds a workflow trigger may subscribe to (the {@code on} catalog). */
    public static Set<String> knownEventKinds() {
        return KNOWN_EVENT_KINDS;
    }

    private static final Pattern CHANGE_REF = Pattern.compile("\\bchange\\.([a-zA-Z][a-zA-Z0-9_]*)");
    private static final Pattern CURRENT_REF = Pattern.compile("\\bcurrent\\.([a-zA-Z][a-zA-Z0-9_]*)");
    private static final Pattern PERSON_REF = Pattern.compile("\\bperson\\.([a-zA-Z][a-zA-Z0-9_]*)");

    private final ExpressionEvaluator expressionEvaluator;

    public DomainEventTriggerValidator(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    /** Returns validation errors; empty means valid. */
    public List<String> validate(Map<String, Object> trigger) {
        ParsedTrigger parsed = ParsedTrigger.from(trigger);
        List<String> errors = new ArrayList<>(parsed.shapeErrors());

        Object reactObj = trigger != null ? trigger.get("reactToEngineEvents") : null;
        if (reactObj != null && !(reactObj instanceof Boolean)) {
            errors.add("trigger.reactToEngineEvents must be a boolean");
        }

        for (TriggerEntry entry : parsed.entries()) {
            validateEntry(entry.on(), entry.filter(), errors);
        }
        return errors;
    }

    private void validateEntry(String on, String filter, List<String> errors) {
        if (on == null || on.isEmpty()) {
            errors.add("trigger.on is required for a domain-event trigger");
        } else if (!KNOWN_EVENT_KINDS.contains(on)) {
            errors.add("trigger.on references unknown event kind: " + on
                    + ". Known: " + new TreeSet<>(KNOWN_EVENT_KINDS));
        }

        if (filter == null) {
            return; // kind-only entry; nothing more to check
        }
        if (filter.isBlank()) {
            errors.add("trigger.filter must be a non-empty string when present");
            return;
        }
        if (!expressionEvaluator.isValidExpression(filter)) {
            errors.add("trigger.filter is not a valid JSONata expression");
            return; // can't reliably field-check an unparseable filter
        }

        Set<String> changeRefs = extract(CHANGE_REF, filter);
        Set<String> currentRefs = extract(CURRENT_REF, filter);
        Set<String> personRefs = extract(PERSON_REF, filter);

        Set<String> diffable = PersonDiffComputer.diffableFieldNames();
        Set<String> captured = PersonUpsertService.capturedFieldNames();

        for (String f : changeRefs) {
            if (!diffable.contains(f)) {
                errors.add("trigger.filter references change." + f
                        + " — not a diffable field; the trigger would never fire.");
            }
        }
        for (String f : currentRefs) {
            if (!captured.contains(f)) {
                errors.add("trigger.filter references current." + f
                        + " — not a captured person field; the trigger would never fire.");
            }
        }
        for (String f : personRefs) {
            if (!captured.contains(f)) {
                errors.add("trigger.filter references person." + f
                        + " — not a captured person field; the trigger would never fire.");
            }
        }

        if (!changeRefs.isEmpty() && !PERSON_STATE_CHANGED.equals(on)) {
            errors.add("trigger.filter uses change.* but on=" + on
                    + " — change.* is only available for person.state_changed.");
        }
        if (!currentRefs.isEmpty() && on != null && !CURRENT_KINDS.contains(on)) {
            errors.add("trigger.filter uses current.* but on=" + on
                    + " — current.* is only available for person.created / person.state_changed.");
        }
    }

    private Set<String> extract(Pattern pattern, String s) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = pattern.matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
