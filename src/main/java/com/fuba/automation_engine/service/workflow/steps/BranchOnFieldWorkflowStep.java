package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BranchOnFieldWorkflowStep implements WorkflowStepType {

    public static final String EXPRESSION_MISSING = "EXPRESSION_MISSING";
    public static final String EXPRESSION_EVAL_ERROR = "EXPRESSION_EVAL_ERROR";
    public static final String NO_MATCHING_RESULT = "NO_MATCHING_RESULT";
    public static final String CONFIG_INVALID = "CONFIG_INVALID";

    private static final Logger log = LoggerFactory.getLogger(BranchOnFieldWorkflowStep.class);

    private final ExpressionEvaluator expressionEvaluator;

    public BranchOnFieldWorkflowStep(ExpressionEvaluator expressionEvaluator) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public String id() {
        return "branch_on_field";
    }

    @Override
    public String displayName() {
        return "Branch on Field";
    }

    @Override
    public String description() {
        return "Evaluate a JSONata expression or a declarative matcher (field/op/values, allOf, anyOf)"
                + " and branch via resultMapping.";
    }

    @Override
    public Map<String, Object> configSchema() {
        // Mode A: expression-driven (existing). Mode B: declarative matcher (leaf + composites).
        // Exactly one of `expression` or {`op` | `allOf` | `anyOf`} must be supplied at top level;
        // mutual-exclusion is enforced at runtime, not by this metadata schema.
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "JSONata expression to evaluate against the run context."
                                        + " Mutually exclusive with op/allOf/anyOf."),
                        "field", Map.of(
                                "type", "string",
                                "description", "JSONata path (e.g. 'person.tags') for the declarative leaf matcher."),
                        "op", Map.of(
                                "type", "string",
                                "enum", Op.configKeys(),
                                "description", "Operator for the declarative leaf matcher."),
                        "values", Map.of(
                                "type", "array",
                                "description", "Required for containsAny/equalsAny; forbidden for exists/missing."),
                        "match", Map.of(
                                "type", "string",
                                "enum", MatchRule.configKeys(),
                                "description", "String compare rule. Default ci-exact. Ignored for non-strings."),
                        "allOf", Map.of(
                                "type", "array",
                                "description", "Composite AND across nested conditions."),
                        "anyOf", Map.of(
                                "type", "array",
                                "description", "Composite OR across nested conditions."),
                        "resultMapping", Map.of(
                                "type", "object",
                                "description", "Map from stringified evaluation result to result code."),
                        "defaultResultCode", Map.of(
                                "type", "string",
                                "description", "Result code when no mapping matches.")));
    }

    @Override
    public Set<String> declaredResultCodes() {
        // Empty signals dynamic result codes — validator skips declared-code check.
        return Set.of();
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();
        if (config == null) {
            config = Map.of();
        }

        Object expressionObj = config.get("expression");
        boolean hasExpression = expressionObj instanceof String s && !s.isBlank();
        boolean hasMatcher = config.containsKey("op")
                || config.containsKey("allOf")
                || config.containsKey("anyOf");

        if (hasExpression && hasMatcher) {
            return StepExecutionResult.failure(CONFIG_INVALID,
                    "Config cannot combine 'expression' with op/allOf/anyOf");
        }
        if (!hasExpression && !hasMatcher) {
            return StepExecutionResult.failure(EXPRESSION_MISSING,
                    "Missing 'expression' (or op/allOf/anyOf matcher) in branch_on_field config");
        }

        ExpressionScope scope = context.runContext() != null
                ? ExpressionScope.from(context.runContext())
                : new ExpressionScope(Map.of());

        if (hasExpression) {
            return executeExpressionMode((String) expressionObj, scope, config, context);
        }
        return executeMatcherMode(config, scope, context);
    }

    private StepExecutionResult executeExpressionMode(
            String expression, ExpressionScope scope, Map<String, Object> config, StepExecutionContext context) {
        Object evalResult;
        try {
            evalResult = expressionEvaluator.evaluatePredicate(expression, scope);
        } catch (RuntimeException ex) {
            log.error("Expression evaluation failed in branch_on_field stepId={} runId={} expression='{}'",
                    context.stepId(), context.runId(), expression, ex);
            return StepExecutionResult.failure(EXPRESSION_EVAL_ERROR,
                    "Expression evaluation failed: " + ex.getMessage());
        }
        String evalResultStr = evalResult != null ? String.valueOf(evalResult) : "null";
        return routeResult(evalResultStr, Map.of("expressionResult", evalResultStr), config);
    }

    private StepExecutionResult executeMatcherMode(
            Map<String, Object> config, ExpressionScope scope, StepExecutionContext context) {
        Condition condition;
        EvalResult result;
        try {
            condition = parseCondition(config);
            result = condition.evaluate(scope, expressionEvaluator);
        } catch (ConfigException ex) {
            return StepExecutionResult.failure(CONFIG_INVALID, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Field-path evaluation failed in branch_on_field stepId={} runId={}",
                    context.stepId(), context.runId(), ex);
            return StepExecutionResult.failure(EXPRESSION_EVAL_ERROR, ex.getMessage());
        }

        String evalResultStr = String.valueOf(result.value());
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("expressionResult", evalResultStr);
        outputs.put("matchResult", result.value());
        // matchedValue is meaningful only when the top-level condition is a leaf.
        if (condition instanceof Leaf && result.matchedValue() != null) {
            outputs.put("matchedValue", result.matchedValue());
        }
        return routeResult(evalResultStr, outputs, config);
    }

    private StepExecutionResult routeResult(
            String evalResultStr, Map<String, Object> outputs, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMapping = config.get("resultMapping") instanceof Map<?, ?>
                ? (Map<String, Object>) config.get("resultMapping")
                : Map.of();

        Object mappedCode = resultMapping.get(evalResultStr);
        if (mappedCode != null) {
            return StepExecutionResult.success(String.valueOf(mappedCode), outputs);
        }
        Object defaultCode = config.get("defaultResultCode");
        if (defaultCode instanceof String defaultResultCode && !defaultResultCode.isBlank()) {
            return StepExecutionResult.success(defaultResultCode, outputs);
        }
        return StepExecutionResult.failure(NO_MATCHING_RESULT,
                "No result mapping matched for result: " + evalResultStr);
    }

    // ---------------- enums ----------------

    enum Op {
        CONTAINS_ANY("containsAny"),
        EQUALS_ANY("equalsAny"),
        EXISTS("exists"),
        MISSING("missing");

        final String configKey;

        Op(String configKey) {
            this.configKey = configKey;
        }

        static Op fromConfigKey(String s) {
            for (Op op : values()) {
                if (op.configKey.equals(s)) {
                    return op;
                }
            }
            throw new ConfigException("Unknown op '" + s + "', expected one of " + configKeys());
        }

        static List<String> configKeys() {
            return Arrays.stream(values()).map(o -> o.configKey).toList();
        }
    }

    enum MatchRule {
        CI_EXACT("ci-exact"),
        CI_CONTAINS("ci-contains"),
        CS_EXACT("cs-exact");

        final String configKey;

        MatchRule(String configKey) {
            this.configKey = configKey;
        }

        static MatchRule fromConfigKey(String s) {
            for (MatchRule m : values()) {
                if (m.configKey.equals(s)) {
                    return m;
                }
            }
            throw new ConfigException("Unknown match rule '" + s + "', expected one of " + configKeys());
        }

        static List<String> configKeys() {
            return Arrays.stream(values()).map(m -> m.configKey).toList();
        }
    }

    // ---------------- condition tree ----------------

    private Condition parseCondition(Map<String, Object> map) {
        boolean hasAllOf = map.containsKey("allOf");
        boolean hasAnyOf = map.containsKey("anyOf");
        boolean hasLeafKey = map.containsKey("op");

        int modes = (hasAllOf ? 1 : 0) + (hasAnyOf ? 1 : 0) + (hasLeafKey ? 1 : 0);
        if (modes == 0) {
            throw new ConfigException("Condition must specify allOf, anyOf, or op");
        }
        if (modes > 1) {
            throw new ConfigException("Condition cannot mix allOf/anyOf/op at the same level");
        }

        if (hasAllOf) {
            return parseComposite(map.get("allOf"), true);
        }
        if (hasAnyOf) {
            return parseComposite(map.get("anyOf"), false);
        }
        return parseLeaf(map);
    }

    private Condition parseComposite(Object raw, boolean isAllOf) {
        String label = isAllOf ? "allOf" : "anyOf";
        if (!(raw instanceof List<?> list)) {
            throw new ConfigException("'" + label + "' must be a list");
        }
        if (list.isEmpty()) {
            throw new ConfigException("'" + label + "' must be non-empty");
        }
        List<Condition> children = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object child = list.get(i);
            if (!(child instanceof Map<?, ?> childMap)) {
                throw new ConfigException("'" + label + "[" + i + "]' must be a condition object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) childMap;
            children.add(parseCondition(typed));
        }
        return isAllOf ? new AllOf(children) : new AnyOf(children);
    }

    private Leaf parseLeaf(Map<String, Object> map) {
        Object fieldObj = map.get("field");
        if (!(fieldObj instanceof String field) || field.isBlank()) {
            throw new ConfigException("Leaf condition requires 'field' (non-blank string)");
        }
        Object opObj = map.get("op");
        if (!(opObj instanceof String opStr)) {
            throw new ConfigException("Leaf condition requires 'op' as one of " + Op.configKeys());
        }
        Op op = Op.fromConfigKey(opStr);

        Object matchObj = map.get("match");
        MatchRule match;
        if (matchObj == null) {
            match = MatchRule.CI_EXACT;
        } else if (matchObj instanceof String mStr) {
            match = MatchRule.fromConfigKey(mStr);
        } else {
            throw new ConfigException("'match' must be one of " + MatchRule.configKeys());
        }

        boolean valuesRequired = op == Op.CONTAINS_ANY || op == Op.EQUALS_ANY;
        boolean valuesForbidden = op == Op.EXISTS || op == Op.MISSING;

        Object valuesObj = map.get("values");
        List<Object> values;
        if (valuesObj == null) {
            if (valuesRequired) {
                throw new ConfigException("'values' is required for op=" + op.configKey);
            }
            values = List.of();
        } else {
            if (valuesForbidden) {
                throw new ConfigException("'values' is not allowed for op=" + op.configKey);
            }
            if (!(valuesObj instanceof List<?> vList)) {
                throw new ConfigException("'values' must be a list");
            }
            if (vList.isEmpty()) {
                throw new ConfigException("'values' must be non-empty");
            }
            values = List.copyOf(vList);
        }

        return new Leaf(field, op, values, match);
    }

    sealed interface Condition permits Leaf, AllOf, AnyOf {
        EvalResult evaluate(ExpressionScope scope, ExpressionEvaluator evaluator);
    }

    record Leaf(String field, Op op, List<Object> values, MatchRule match) implements Condition {
        @Override
        public EvalResult evaluate(ExpressionScope scope, ExpressionEvaluator evaluator) {
            Object resolved = evaluator.evaluatePredicate(field, scope);
            return switch (op) {
                case EXISTS -> existsCheck(resolved) ? EvalResult.pass() : EvalResult.fail();
                case MISSING -> existsCheck(resolved) ? EvalResult.fail() : EvalResult.pass();
                case EQUALS_ANY -> evalEqualsAny(resolved);
                case CONTAINS_ANY -> evalContainsAny(resolved);
            };
        }

        private boolean existsCheck(Object value) {
            if (value == null) {
                return false;
            }
            if (value instanceof String s) {
                return !s.isBlank();
            }
            return true;
        }

        private EvalResult evalEqualsAny(Object resolved) {
            if (resolved instanceof List<?>) {
                throw new ConfigException("equalsAny cannot be used on a list-valued field: " + field);
            }
            if (resolved == null) {
                return EvalResult.fail();
            }
            for (Object v : values) {
                if (compare(resolved, v)) {
                    return EvalResult.passMatched(String.valueOf(v));
                }
            }
            return EvalResult.fail();
        }

        private EvalResult evalContainsAny(Object resolved) {
            Iterable<?> fieldItems;
            if (resolved == null) {
                fieldItems = List.of();
            } else if (resolved instanceof List<?> l) {
                fieldItems = l;
            } else {
                fieldItems = List.of(resolved);
            }
            for (Object item : fieldItems) {
                for (Object v : values) {
                    if (compare(item, v)) {
                        return EvalResult.passMatched(String.valueOf(v));
                    }
                }
            }
            return EvalResult.fail();
        }

        private boolean compare(Object fieldVal, Object configVal) {
            if (fieldVal == null || configVal == null) {
                return Objects.equals(fieldVal, configVal);
            }
            if (fieldVal instanceof String s && configVal instanceof String c) {
                return switch (match) {
                    case CI_EXACT -> s.equalsIgnoreCase(c);
                    case CI_CONTAINS -> s.toLowerCase().contains(c.toLowerCase());
                    case CS_EXACT -> s.equals(c);
                };
            }
            // Cross-type numeric tolerance: Integer 1 vs Long 1 etc.
            if (fieldVal instanceof Number fn && configVal instanceof Number cn) {
                return fn.doubleValue() == cn.doubleValue();
            }
            return Objects.equals(fieldVal, configVal);
        }
    }

    record AllOf(List<Condition> children) implements Condition {
        @Override
        public EvalResult evaluate(ExpressionScope scope, ExpressionEvaluator evaluator) {
            for (Condition c : children) {
                if (!c.evaluate(scope, evaluator).value()) {
                    return EvalResult.fail();
                }
            }
            return EvalResult.pass();
        }
    }

    record AnyOf(List<Condition> children) implements Condition {
        @Override
        public EvalResult evaluate(ExpressionScope scope, ExpressionEvaluator evaluator) {
            for (Condition c : children) {
                if (c.evaluate(scope, evaluator).value()) {
                    return EvalResult.pass();
                }
            }
            return EvalResult.fail();
        }
    }

    record EvalResult(boolean value, String matchedValue) {
        static EvalResult pass() {
            return new EvalResult(true, null);
        }

        static EvalResult passMatched(String matchedValue) {
            return new EvalResult(true, matchedValue);
        }

        static EvalResult fail() {
            return new EvalResult(false, null);
        }
    }

    static final class ConfigException extends RuntimeException {
        ConfigException(String message) {
            super(message);
        }
    }
}
