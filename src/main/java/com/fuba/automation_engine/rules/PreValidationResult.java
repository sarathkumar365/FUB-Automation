package com.fuba.automation_engine.rules;

public record PreValidationResult(
        CallDecisionAction action,
        String reasonCode) {
}
