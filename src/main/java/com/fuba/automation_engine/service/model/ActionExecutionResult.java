package com.fuba.automation_engine.service.model;

public record ActionExecutionResult(
        boolean success,
        String reasonCode,
        String message) {

    public static ActionExecutionResult ok() {
        return new ActionExecutionResult(true, null, null);
    }

    public static ActionExecutionResult failure(String reasonCode, String message) {
        return new ActionExecutionResult(false, reasonCode, message);
    }
}
