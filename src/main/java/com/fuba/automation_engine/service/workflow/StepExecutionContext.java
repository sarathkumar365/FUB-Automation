package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record StepExecutionContext(
        long runId,
        long stepId,
        String nodeId,
        String sourceLeadId,
        Map<String, Object> rawConfig,
        Map<String, Object> resolvedConfig,
        RunContext runContext,
        Map<String, Object> stepState) {

    public StepExecutionContext {
        rawConfig = rawConfig != null ? rawConfig : Map.of();
        resolvedConfig = resolvedConfig != null ? resolvedConfig : Map.of();
        stepState = stepState != null ? stepState : Map.of();
    }

    public StepExecutionContext(
            long runId,
            long stepId,
            String nodeId,
            String sourceLeadId,
            Map<String, Object> rawConfig,
            Map<String, Object> resolvedConfig,
            RunContext runContext) {
        this(runId, stepId, nodeId, sourceLeadId, rawConfig, resolvedConfig, runContext, Map.of());
    }
}
