package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import com.fuba.automation_engine.service.workflow.expression.ExpressionEvaluator;
import com.fuba.automation_engine.service.workflow.expression.ExpressionScope;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BranchOnFieldWorkflowStep implements WorkflowStepType {

    static final String EXPRESSION_MISSING = "EXPRESSION_MISSING";
    static final String EXPRESSION_EVAL_ERROR = "EXPRESSION_EVAL_ERROR";
    static final String NO_MATCHING_RESULT = "NO_MATCHING_RESULT";

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
        return "Evaluate an expression and branch based on the result. Maps expression results to result codes.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "JSONata expression to evaluate against the run context"),
                        "resultMapping", Map.of(
                                "type", "object",
                                "description", "Map from expression result (as string) to result code"),
                        "defaultResultCode", Map.of(
                                "type", "string",
                                "description", "Result code to use when no mapping matches")),
                "required", List.of("expression"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        // Empty signals dynamic result codes — validator skips declared-code check
        return Set.of();
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();

        Object expressionObj = config.get("expression");
        if (!(expressionObj instanceof String expression) || expression.isBlank()) {
            return StepExecutionResult.failure(EXPRESSION_MISSING, "Missing 'expression' in branch_on_field config");
        }

        ExpressionScope scope = context.runContext() != null
                ? ExpressionScope.from(context.runContext())
                : new ExpressionScope(Map.of());

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

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMapping = config.get("resultMapping") instanceof Map<?, ?>
                ? (Map<String, Object>) config.get("resultMapping")
                : Map.of();

        Object mappedCode = resultMapping.get(evalResultStr);
        if (mappedCode != null) {
            String resultCode = String.valueOf(mappedCode);
            return StepExecutionResult.success(resultCode, Map.of("expressionResult", evalResultStr));
        }

        Object defaultCode = config.get("defaultResultCode");
        if (defaultCode instanceof String defaultResultCode && !defaultResultCode.isBlank()) {
            return StepExecutionResult.success(defaultResultCode, Map.of("expressionResult", evalResultStr));
        }

        return StepExecutionResult.failure(NO_MATCHING_RESULT,
                "No result mapping matched for expression result: " + evalResultStr);
    }
}
