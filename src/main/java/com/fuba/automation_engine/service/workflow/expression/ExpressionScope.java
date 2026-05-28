package com.fuba.automation_engine.service.workflow.expression;

import com.fuba.automation_engine.service.workflow.RunContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bag of variables that JSONata expressions evaluate against. Pure mapper:
 * each top-level key is plucked from a pre-resolved field on {@link RunContext}.
 * Data resolution (DB reads, FUB calls, time computation, etc.) belongs in
 * {@code WorkflowStepExecutionService.buildRunContext}, not here.
 *
 * <p>Top-level keys exposed to workflow authors:
 * <ul>
 *   <li>{@code event.payload} — the webhook payload that started the run</li>
 *   <li>{@code sourcePersonId} — string ID of the person the run operates on</li>
 *   <li>{@code person} — locally-snapshotted person details (Map of
 *       {@code persons.person_details} plus {@code kind}); empty map if no snapshot
 *       exists. Always present in scope for consistency, even when empty.</li>
 *   <li>{@code now} — time-of-day flags pre-resolved by
 *       {@code BusinessHoursService}: {@code now.isDaytime} (bool) and
 *       {@code now.hourLocal} (int 0-23). Always present.</li>
 *   <li>{@code steps.<nodeId>.outputs.<key>} — outputs from prior steps</li>
 * </ul>
 */
public record ExpressionScope(Map<String, Object> data) {

    public static ExpressionScope from(RunContext runContext) {
        Map<String, Object> scope = new LinkedHashMap<>();

        // Workflow expressions only see the trigger payload passed at plan time.
        // There is no automatic hydration of local person snapshots here.
        scope.put("event", Map.of("payload",
                runContext.triggerPayload() != null ? runContext.triggerPayload() : Map.of()));

        scope.put("sourcePersonId", runContext.sourcePersonId() != null ? runContext.sourcePersonId() : "");

        scope.put("person", runContext.person() != null ? runContext.person() : Map.of());

        scope.put("now", runContext.now() != null ? runContext.now() : Map.of());

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
