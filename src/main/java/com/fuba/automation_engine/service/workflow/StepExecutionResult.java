package com.fuba.automation_engine.service.workflow;

import java.time.OffsetDateTime;
import java.util.Map;

public record StepExecutionResult(
        boolean success,
        String resultCode,
        Map<String, Object> outputs,
        String errorMessage,
        boolean transientFailure,
        boolean reschedule,
        OffsetDateTime nextDueAt,
        Map<String, Object> statePatch) {

    public static StepExecutionResult success(String resultCode, Map<String, Object> outputs) {
        return new StepExecutionResult(true, resultCode, outputs, null, false, false, null, Map.of());
    }

    public static StepExecutionResult success(String resultCode) {
        return new StepExecutionResult(true, resultCode, Map.of(), null, false, false, null, Map.of());
    }

    public static StepExecutionResult failure(String resultCode, String errorMessage) {
        return new StepExecutionResult(false, resultCode, Map.of(), errorMessage, false, false, null, Map.of());
    }

    public static StepExecutionResult transientFailure(String resultCode, String errorMessage) {
        return new StepExecutionResult(false, resultCode, Map.of(), errorMessage, true, false, null, Map.of());
    }

    public static StepExecutionResult reschedule(OffsetDateTime nextDueAt, Map<String, Object> statePatch) {
        return new StepExecutionResult(true, null, Map.of(), null, false, true, nextDueAt,
                statePatch != null ? statePatch : Map.of());
    }
}
