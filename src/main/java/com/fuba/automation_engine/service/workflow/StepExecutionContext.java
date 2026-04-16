package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record StepExecutionContext(
        long runId,
        long stepId,
        String nodeId,
        String sourceLeadId,
        Map<String, Object> rawConfig,
        Map<String, Object> resolvedConfig,
        RunContext runContext) {
}
