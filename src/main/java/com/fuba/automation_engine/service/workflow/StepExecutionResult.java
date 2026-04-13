package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record StepExecutionResult(
        boolean success,
        String resultCode,
        Map<String, Object> outputs,
        String errorMessage) {

    public static StepExecutionResult success(String resultCode, Map<String, Object> outputs) {
        return new StepExecutionResult(true, resultCode, outputs, null);
    }

    public static StepExecutionResult success(String resultCode) {
        return new StepExecutionResult(true, resultCode, Map.of(), null);
    }

    public static StepExecutionResult failure(String resultCode, String errorMessage) {
        return new StepExecutionResult(false, resultCode, Map.of(), errorMessage);
    }
}
