package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.event.DomainEvent;
import com.fuba.automation_engine.service.person.PersonSnapshotResolver;
import com.fuba.automation_engine.service.workflow.expression.DomainEventScopeBuilder;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Trigger that fires on domain events. Deliberately standalone — it does NOT
 * implement the webhook-shaped {@link WorkflowTriggerType}, which is deleted in
 * Phase 4d along with the rest of the webhook trigger path. Config shape:
 * {@code { "on": "<event_kind>", "filter": "<JSONata>" }}.
 *
 * <p>Built in 4b but not yet wired to the dispatcher — that happens in 4d.
 */
@Component
public class DomainEventTriggerType {

    public static final String TRIGGER_TYPE_ID = "domain_event";

    private static final String CONFIG_ON = "on";
    private static final String CONFIG_FILTER = "filter";
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
        String on = asString(config.get(CONFIG_ON));
        if (on == null || !on.equals(event.eventKind())) {
            return false;
        }
        String filter = asString(config.get(CONFIG_FILTER));
        if (filter == null || filter.isBlank()) {
            return true; // kind matched and no further predicate
        }
        Map<String, Object> scope = scopeBuilder.build(event, resolvePerson(event));
        Object result = expressionEvaluator.evaluatePredicate(filter, new ExpressionScope(scope));
        return isTruthy(result);
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

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
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
