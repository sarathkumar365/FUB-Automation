package com.fuba.automation_engine.rules;

public record CallDecision(
        CallDecisionAction action,
        String ruleApplied,
        String reasonCode) {
}
