package com.fuba.automation_engine.service.workflow;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void shouldReturnFallbackWhenMapIsNull() {
        RetryPolicy fallback = RetryPolicy.DEFAULT_FUB;
        RetryPolicy policy = RetryPolicy.fromMap(null, fallback);
        assertEquals(fallback, policy);
    }

    @Test
    void shouldParseTypedValuesFromMap() {
        RetryPolicy policy = RetryPolicy.fromMap(
                Map.of(
                        "maxAttempts", 5,
                        "initialBackoffMs", 200L,
                        "backoffMultiplier", 3.0,
                        "maxBackoffMs", 8000L,
                        "retryOnTransient", false),
                RetryPolicy.NO_RETRY);

        assertEquals(5, policy.maxAttempts());
        assertEquals(200L, policy.initialBackoffMs());
        assertEquals(3.0, policy.backoffMultiplier());
        assertEquals(8000L, policy.maxBackoffMs());
        assertFalse(policy.retryOnTransient());
    }

    @Test
    void shouldParseStringValuesWhenPossible() {
        RetryPolicy policy = RetryPolicy.fromMap(
                Map.of(
                        "maxAttempts", "4",
                        "initialBackoffMs", "250",
                        "backoffMultiplier", "1.5",
                        "maxBackoffMs", "4000",
                        "retryOnTransient", "true"),
                RetryPolicy.NO_RETRY);

        assertEquals(4, policy.maxAttempts());
        assertEquals(250L, policy.initialBackoffMs());
        assertEquals(1.5, policy.backoffMultiplier());
        assertEquals(4000L, policy.maxBackoffMs());
        assertTrue(policy.retryOnTransient());
    }

    @Test
    void shouldFallbackWhenValuesAreInvalid() {
        RetryPolicy fallback = RetryPolicy.DEFAULT_FUB;
        RetryPolicy policy = RetryPolicy.fromMap(
                Map.of(
                        "maxAttempts", "abc",
                        "initialBackoffMs", -1,
                        "backoffMultiplier", 0,
                        "maxBackoffMs", -10,
                        "retryOnTransient", "not-a-bool"),
                fallback);

        assertEquals(fallback.maxAttempts(), policy.maxAttempts());
        assertEquals(fallback.initialBackoffMs(), policy.initialBackoffMs());
        assertEquals(fallback.backoffMultiplier(), policy.backoffMultiplier());
        assertEquals(fallback.maxBackoffMs(), policy.maxBackoffMs());
        assertEquals(fallback.retryOnTransient(), policy.retryOnTransient());
    }
}
