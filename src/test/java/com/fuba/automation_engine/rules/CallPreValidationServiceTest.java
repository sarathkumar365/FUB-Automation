package com.fuba.automation_engine.rules;

import com.fuba.automation_engine.service.model.CallDetails;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallPreValidationServiceTest {

    private CallPreValidationService service;

    @BeforeEach
    void setUp() {
        service = new CallPreValidationService();
    }

    @Test
    void shouldSkipWhenUserIdMissing() {
        Optional<PreValidationResult> result = service.validate(new CallDetails(1L, 20L, 0, 0L, "No Answer"));

        assertTrue(result.isPresent());
        assertEquals(CallDecisionAction.SKIP, result.get().action());
        assertEquals(CallDecisionEngine.REASON_MISSING_ASSIGNEE, result.get().reasonCode());
    }

    @Test
    void shouldFailWhenDurationNegative() {
        Optional<PreValidationResult> result = service.validate(new CallDetails(1L, 20L, -1, 3L, "No Answer"));

        assertTrue(result.isPresent());
        assertEquals(CallDecisionAction.FAIL, result.get().action());
        assertEquals(CallDecisionEngine.REASON_INVALID_DURATION, result.get().reasonCode());
    }

    @Test
    void shouldNormalizePersonAndOutcome() {
        ValidatedCallContext context = service.normalize(new CallDetails(1L, 0L, 10, 3L, "  No Answer  "));

        assertEquals(1L, context.callId());
        assertNull(context.personId());
        assertEquals(10, context.duration());
        assertEquals(3L, context.userId());
        assertEquals("no answer", context.normalizedOutcome());
    }

    @Test
    void shouldNormalizeBlankOutcomeToNull() {
        ValidatedCallContext context = service.normalize(new CallDetails(1L, 12L, 10, 3L, "   "));
        assertNull(context.normalizedOutcome());
    }
}
