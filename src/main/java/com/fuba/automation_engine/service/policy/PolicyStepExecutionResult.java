package com.fuba.automation_engine.service.policy;

public record PolicyStepExecutionResult(
        boolean success,
        PolicyStepResultCode resultCode,
        String reasonCode,
        String errorMessage) {

    public static PolicyStepExecutionResult success(PolicyStepResultCode resultCode) {
        return new PolicyStepExecutionResult(true, resultCode, null, null);
    }

    public static PolicyStepExecutionResult failure(String reasonCode, String errorMessage) {
        return new PolicyStepExecutionResult(false, null, reasonCode, errorMessage);
    }
}
