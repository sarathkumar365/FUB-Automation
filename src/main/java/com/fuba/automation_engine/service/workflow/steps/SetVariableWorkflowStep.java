package com.fuba.automation_engine.service.workflow.steps;

import com.fuba.automation_engine.service.workflow.StepExecutionContext;
import com.fuba.automation_engine.service.workflow.StepExecutionResult;
import com.fuba.automation_engine.service.workflow.WorkflowStepType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SetVariableWorkflowStep implements WorkflowStepType {

    static final String VARIABLE_NAME_MISSING = "VARIABLE_NAME_MISSING";

    @Override
    public String id() {
        return "set_variable";
    }

    @Override
    public String displayName() {
        return "Set Variable";
    }

    @Override
    public String description() {
        return "Write a value into the run context for downstream steps to read.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "variableName", Map.of(
                                "type", "string",
                                "description", "Name of the variable to set"),
                        "value", Map.of(
                                "description", "Value to assign (any type, supports template expressions)")),
                "required", List.of("variableName", "value"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("DONE");
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Map<String, Object> config = context.resolvedConfig() != null ? context.resolvedConfig() : context.rawConfig();

        Object variableNameObj = config.get("variableName");
        if (!(variableNameObj instanceof String variableName) || variableName.isBlank()) {
            return StepExecutionResult.failure(VARIABLE_NAME_MISSING, "Missing 'variableName' in set_variable config");
        }

        Object value = config.get("value");
        return StepExecutionResult.success("DONE", Map.of(variableName, value != null ? value : ""));
    }
}
