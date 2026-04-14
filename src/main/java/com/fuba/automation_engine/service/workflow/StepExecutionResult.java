package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record StepExecutionResult(
        boolean success,
        String resultCode,
        Map<String, Object> outputs,
        String errorMessage,
        boolean transientFailure) {

    public static StepExecutionResult success(String resultCode, Map<String, Object> outputs) {
        return new StepExecutionResult(true, resultCode, outputs, null, false);
    }

    public static StepExecutionResult success(String resultCode) {
        return new StepExecutionResult(true, resultCode, Map.of(), null, false);
    }

    public static StepExecutionResult failure(String resultCode, String errorMessage) {
        return new StepExecutionResult(false, resultCode, Map.of(), errorMessage, false);
    }

    public static StepExecutionResult transientFailure(String resultCode, String errorMessage) {
        return new StepExecutionResult(false, resultCode, Map.of(), errorMessage, true);
    }
}
