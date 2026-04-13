package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record RunContext(
        Map<String, Object> triggerPayload,
        String sourceLeadId,
        Map<String, Map<String, Object>> stepOutputs) {
}
