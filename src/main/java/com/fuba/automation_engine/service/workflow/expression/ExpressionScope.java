package com.fuba.automation_engine.service.workflow.expression;

import com.fuba.automation_engine.service.workflow.RunContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public record ExpressionScope(Map<String, Object> data) {

    public static ExpressionScope from(RunContext runContext) {
        Map<String, Object> scope = new LinkedHashMap<>();

        // Workflow expressions only see the trigger payload passed at plan time.
        // There is no automatic hydration of local lead snapshots here.
        scope.put("event", Map.of("payload",
                runContext.triggerPayload() != null ? runContext.triggerPayload() : Map.of()));

        scope.put("sourceLeadId", runContext.sourceLeadId() != null ? runContext.sourceLeadId() : "");

        Map<String, Object> stepsMap = new LinkedHashMap<>();
        if (runContext.stepOutputs() != null) {
            for (Map.Entry<String, Map<String, Object>> entry : runContext.stepOutputs().entrySet()) {
                Map<String, Object> stepData = new HashMap<>();
                stepData.put("outputs", entry.getValue() != null ? entry.getValue() : Map.of());
                stepsMap.put(entry.getKey(), stepData);
            }
        }
        scope.put("steps", stepsMap);

        return new ExpressionScope(Map.copyOf(scope));
    }
}
