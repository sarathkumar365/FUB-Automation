package com.fuba.automation_engine.service.workflow.trigger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes a trigger map into a list of {@link TriggerEntry} (flat
 * {@code {on, filter}} → one entry, {@code {anyOf: [...]}} → N) and collects its
 * structural shape errors. The single place that interprets trigger shape,
 * shared by the router, matcher, and validator.
 */
record ParsedTrigger(List<TriggerEntry> entries, boolean isAnyOf, List<String> shapeErrors) {

    private static final String KEY_ON = "on";
    private static final String KEY_ANY_OF = "anyOf";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_REACT = "reactToEngineEvents";

    static ParsedTrigger from(Map<String, Object> trigger) {
        List<TriggerEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (trigger == null) {
            errors.add("trigger is required");
            return new ParsedTrigger(List.of(), false, List.copyOf(errors));
        }

        boolean hasOn = trigger.containsKey(KEY_ON);
        boolean hasAnyOf = trigger.containsKey(KEY_ANY_OF);
        if (hasOn == hasAnyOf) {
            errors.add(hasOn
                    ? "trigger must declare exactly one of 'on' or 'anyOf', not both"
                    : "trigger must declare one of 'on' or 'anyOf'");
        }

        if (hasAnyOf) {
            collectAnyOf(trigger.get(KEY_ANY_OF), entries, errors);
            return new ParsedTrigger(List.copyOf(entries), true, List.copyOf(errors));
        }
        if (hasOn) {
            entries.add(new TriggerEntry(str(trigger.get(KEY_ON)), str(trigger.get(KEY_FILTER))));
        }
        return new ParsedTrigger(List.copyOf(entries), false, List.copyOf(errors));
    }

    boolean subscribesTo(String eventKind) {
        if (eventKind == null) {
            return false;
        }
        for (TriggerEntry entry : entries) {
            if (eventKind.equals(entry.on())) {
                return true;
            }
        }
        return false;
    }

    private static void collectAnyOf(Object raw, List<TriggerEntry> entries, List<String> errors) {
        if (!(raw instanceof List<?> list)) {
            errors.add("trigger.anyOf must be an array");
            return;
        }
        if (list.isEmpty()) {
            errors.add("trigger.anyOf must be a non-empty array");
            return;
        }
        Set<String> seenKinds = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> entryMap)) {
                errors.add("trigger.anyOf[" + i + "] must be an object");
                continue;
            }
            String on = str(entryMap.get(KEY_ON));
            String filter = str(entryMap.get(KEY_FILTER));
            if (on == null || on.isBlank()) {
                errors.add("trigger.anyOf[" + i + "].on is required");
            } else if (!seenKinds.add(on)) {
                errors.add("trigger.anyOf has duplicate kind: " + on);
            }
            if (entryMap.containsKey(KEY_REACT)) {
                errors.add("trigger.anyOf[" + i + "].reactToEngineEvents is not allowed; "
                        + "set reactToEngineEvents at the top level");
            }
            entries.add(new TriggerEntry(on, filter));
        }
    }

    private static String str(Object value) {
        return (value instanceof String s) ? s.trim() : null;
    }
}
