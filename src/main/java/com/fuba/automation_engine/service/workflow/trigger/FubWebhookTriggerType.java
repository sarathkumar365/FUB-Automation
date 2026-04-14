package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FubWebhookTriggerType implements WorkflowTriggerType {

    public static final String TRIGGER_TYPE_ID = "webhook_fub";

    private final ExpressionEvaluator expressionEvaluator;

    public FubWebhookTriggerType(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public String id() {
        return TRIGGER_TYPE_ID;
    }

    @Override
    public String displayName() {
        return "FUB Webhook Trigger";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("eventDomain", "eventAction"),
                "properties", Map.of(
                        "eventDomain", Map.of("type", "string"),
                        "eventAction", Map.of("type", "string"),
                        "filter", Map.of("type", "string")));
    }

    @Override
    public boolean matches(TriggerMatchContext context) {
        if (context == null || context.source() != WebhookSource.FUB) {
            return false;
        }

        String eventDomainPattern = normalizePattern(readString(context.triggerConfig(), "eventDomain"));
        String eventActionPattern = normalizePattern(readString(context.triggerConfig(), "eventAction"));
        if (eventDomainPattern == null || eventActionPattern == null) {
            return false;
        }

        if (!patternMatches(eventDomainPattern, enumName(context.normalizedDomain()))) {
            return false;
        }
        if (!patternMatches(eventActionPattern, enumName(context.normalizedAction()))) {
            return false;
        }

        String filter = readString(context.triggerConfig(), "filter");
        if (filter == null || filter.isBlank()) {
            return true;
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("event", Map.of("payload", context.payload() != null ? context.payload() : Map.of()));
        Object result = expressionEvaluator.evaluatePredicate(filter, new ExpressionScope(Map.copyOf(scope)));
        return isTruthy(result);
    }

    @Override
    public List<EntityRef> extractEntities(TriggerMatchContext context) {
        Object idsValue = context != null && context.payload() != null
                ? context.payload().get("resourceIds")
                : null;
        if (!(idsValue instanceof List<?> ids) || ids.isEmpty()) {
            return List.of();
        }

        List<EntityRef> entities = new ArrayList<>();
        for (Object id : ids) {
            if (id == null) {
                continue;
            }
            String entityId = String.valueOf(id).trim();
            if (!entityId.isEmpty()) {
                entities.add(new EntityRef("lead", entityId));
            }
        }
        return List.copyOf(entities);
    }

    private String readString(Map<String, Object> config, String key) {
        if (config == null) {
            return null;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String normalizePattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private boolean patternMatches(String pattern, String value) {
        return Objects.equals(pattern, "*") || Objects.equals(pattern, value);
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
