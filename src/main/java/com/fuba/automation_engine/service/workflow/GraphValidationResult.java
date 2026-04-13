package com.fuba.automation_engine.service.workflow;

import java.util.List;

public record GraphValidationResult(
        boolean valid,
        List<String> errors) {

    public static GraphValidationResult success() {
        return new GraphValidationResult(true, List.of());
    }

    public static GraphValidationResult failure(List<String> errors) {
        return new GraphValidationResult(false, errors);
    }

    public static GraphValidationResult failure(String error) {
        return new GraphValidationResult(false, List.of(error));
    }
}
