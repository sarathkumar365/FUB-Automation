package com.fuba.automation_engine.rules;

public record ValidatedCallContext(
        Long callId,
        Long personId,
        Integer duration,
        Long userId,
        String normalizedOutcome) {
}
