package com.flux.service.workflow.steps;

import com.flux.service.workflow.StepCategory;
import com.flux.service.workflow.StepExecutionContext;
import com.flux.service.workflow.StepExecutionResult;
import com.flux.service.workflow.WorkflowStepType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DelayWorkflowStep implements WorkflowStepType {

    @Override
    public StepCategory category() {
        return StepCategory.CONTROL;
    }

    @Override
    public String id() {
        return "delay";
    }

    @Override
    public String displayName() {
        return "Delay";
    }

    @Override
    public String description() {
        return "Wait for a specified duration before proceeding.";
    }

    @Override
    public Map<String, Object> configSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "delayMinutes", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "description", "Minutes to wait")),
                "required", List.of("delayMinutes"));
    }

    @Override
    public Set<String> declaredResultCodes() {
        return Set.of("DONE");
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        // Delay is handled by due_at at materialization time.
        // By the time execute() is called, the delay has already elapsed.
        return StepExecutionResult.success("DONE");
    }
}
