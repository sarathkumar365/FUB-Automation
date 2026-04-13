package com.fuba.automation_engine.service.workflow;

public record RetryPolicy(
        int maxAttempts,
        long initialBackoffMs,
        double backoffMultiplier,
        long maxBackoffMs,
        boolean retryOnTransient) {

    public static final RetryPolicy NO_RETRY = new RetryPolicy(1, 0, 1.0, 0, false);
    public static final RetryPolicy DEFAULT_FUB = new RetryPolicy(3, 500, 2.0, 5000, true);
}
