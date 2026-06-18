package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.event.DomainEvent;
import com.fuba.automation_engine.service.person.PersonSnapshotResolver;
import com.fuba.automation_engine.service.workflow.expression.DomainEventScopeBuilder;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Trigger that fires on domain events. Reads both the flat
 * {@code { "on": "<kind>", "filter": "<JSONata>" }} and the
 * {@code { "anyOf": [ { "on": ..., "filter": ... }, ... ] }} shapes through
 * {@link ParsedTrigger}; the workflow fires if any entry matches.
 */
@Component
public class DomainEventTriggerType {

    private static final Logger log = LoggerFactory.getLogger(DomainEventTriggerType.class);

    public static final String TRIGGER_TYPE_ID = "domain_event";

    private static final String ENTITY_TYPE_PERSON = "person";

    private final PersonSnapshotResolver personSnapshotResolver;
    private final DomainEventScopeBuilder scopeBuilder;
    private final ExpressionEvaluator expressionEvaluator;

    public DomainEventTriggerType(
            PersonSnapshotResolver personSnapshotResolver,
            DomainEventScopeBuilder scopeBuilder,
            ExpressionEvaluator expressionEvaluator) {
        this.personSnapshotResolver = personSnapshotResolver;
        this.scopeBuilder = scopeBuilder;
        this.expressionEvaluator = expressionEvaluator;
    }

    public String id() {
        return TRIGGER_TYPE_ID;
    }

    public boolean matches(DomainEvent event, Map<String, Object> config) {
        if (event == null || config == null) {
            return false;
        }
        ParsedTrigger parsed = ParsedTrigger.from(config);
        Map<String, Object> scope = null;
        for (TriggerEntry entry : parsed.entries()) {
            if (entry.on() == null || !entry.on().equals(event.eventKind())) {
                continue;
            }
            String filter = entry.filter();
            if (filter == null || filter.isBlank()) {
                return true; // kind matched and no further predicate
            }
            if (scope == null) {
                scope = scopeBuilder.build(event, resolvePerson(event));
            }
            try {
                Object result = expressionEvaluator.evaluatePredicate(filter, new ExpressionScope(scope));
                if (isTruthy(result)) {
                    return true;
                }
            } catch (RuntimeException ex) {
                // a failing filter is a non-match, not a veto on the sibling entries
                log.warn("Trigger filter errored; treating entry as non-match eventId={} kind={} on={}",
                        event.id(), event.eventKind(), entry.on(), ex);
            }
        }
        return false;
    }

    public List<EntityRef> extractEntities(DomainEvent event) {
        if (event == null || event.entityType() == null || event.entityId() == null) {
            return List.of();
        }
        return List.of(new EntityRef(event.entityType(), event.entityId()));
    }

    private Map<String, Object> resolvePerson(DomainEvent event) {
        if (ENTITY_TYPE_PERSON.equals(event.entityType()) && event.entityId() != null) {
            return personSnapshotResolver.resolve(event.entityId());
        }
        return Map.of();
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence text) {
            return !text.toString().trim().isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
