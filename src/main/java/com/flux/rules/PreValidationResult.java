package com.flux.rules;

public record PreValidationResult(
        CallDecisionAction action,
        String reasonCode) {
}
