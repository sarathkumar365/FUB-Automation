package com.flux.rules;

public record CallDecision(
        CallDecisionAction action,
        String ruleApplied,
        String reasonCode) {
}
