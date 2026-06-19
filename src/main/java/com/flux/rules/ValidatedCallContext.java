package com.flux.rules;

public record ValidatedCallContext(
        Long callId,
        Long personId,
        Integer duration,
        Long userId,
        String normalizedOutcome) {
}
