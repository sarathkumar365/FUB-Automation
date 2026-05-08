package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record RunContext(
        RunMetadata metadata,
        Map<String, Object> triggerPayload,
        String sourceLeadId,
        Map<String, Map<String, Object>> stepOutputs) {

    public record RunMetadata(long runId, String workflowKey, long workflowVersion) {}
}
