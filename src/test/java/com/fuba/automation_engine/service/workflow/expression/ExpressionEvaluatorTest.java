package com.fuba.automation_engine.service.workflow.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fuba.automation_engine.service.workflow.RunContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {

    private JsonataExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JsonataExpressionEvaluator();
    }

    private ExpressionScope buildScope(Map<String, Object> triggerPayload,
                                       String sourceLeadId,
                                       Map<String, Map<String, Object>> stepOutputs) {
        RunContext runContext = new RunContext(null, triggerPayload, sourceLeadId, stepOutputs);
        return ExpressionScope.from(runContext);
    }

    @Test
    void shouldResolveSimplePropertyAccess() {
        ExpressionScope scope = buildScope(
                Map.of("name", "Alice"), "123", Map.of());

        Object result = evaluator.resolveTemplate("{{ event.payload.name }}", scope);
        assertEquals("Alice", result);
    }

    @Test
    void shouldPreserveNumericTypeForWholeStringTemplate() {
        ExpressionScope scope = buildScope(
                Map.of("userId", 42), "123", Map.of());

        Object result = evaluator.resolveTemplate("{{ event.payload.userId }}", scope);
        assertInstanceOf(Number.class, result);
        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    void shouldResolveEmbeddedTemplatesAsString() {
        ExpressionScope scope = buildScope(
                Map.of("name", "Alice", "id", 42), "123", Map.of());

        Object result = evaluator.resolveTemplate(
                "Hello {{ event.payload.name }}, your id is {{ event.payload.id }}", scope);
        assertEquals("Hello Alice, your id is 42", result);
    }

    @Test
    void shouldResolveStepOutputReference() {
        ExpressionScope scope = buildScope(
                Map.of(), "123",
                Map.of("check_claim", Map.of("assignedUserId", 77)));

        Object result = evaluator.resolveTemplate("{{ steps.check_claim.outputs.assignedUserId }}", scope);
        assertInstanceOf(Number.class, result);
        assertEquals(77, ((Number) result).intValue());
    }

    @Test
    void shouldResolveSourceLeadId() {
        ExpressionScope scope = buildScope(Map.of(), "7890", Map.of());

        Object result = evaluator.resolveTemplate("{{ sourceLeadId }}", scope);
        assertEquals("7890", result);
    }

    @Test
    void shouldEvaluatePredicateTrue() {
        ExpressionScope scope = buildScope(
                Map.of(), "123",
                Map.of("check_claim", Map.of("assignedUserId", 77)));

        Object result = evaluator.evaluatePredicate("steps.check_claim.outputs.assignedUserId > 0", scope);
        assertEquals(true, result);
    }

    @Test
    void shouldEvaluatePredicateFalse() {
        ExpressionScope scope = buildScope(
                Map.of(), "123",
                Map.of("check_claim", Map.of("assignedUserId", 0)));

        Object result = evaluator.evaluatePredicate("steps.check_claim.outputs.assignedUserId > 0", scope);
        assertEquals(false, result);
    }

    @Test
    void shouldReturnNullForMissingPath() {
        ExpressionScope scope = buildScope(Map.of(), "123", Map.of());

        Object result = evaluator.resolveTemplate("{{ event.payload.nonexistent }}", scope);
        assertNull(result);
    }

    @Test
    void shouldPassthroughStringWithoutTemplateMarkers() {
        ExpressionScope scope = buildScope(Map.of(), "123", Map.of());

        Object result = evaluator.resolveTemplate("plain string value", scope);
        assertEquals("plain string value", result);
    }

    @Test
    void shouldHandleNullTemplate() {
        ExpressionScope scope = buildScope(Map.of(), "123", Map.of());

        Object result = evaluator.resolveTemplate(null, scope);
        assertNull(result);
    }

    @Test
    void shouldHandleNullPredicate() {
        ExpressionScope scope = buildScope(Map.of(), "123", Map.of());

        Object result = evaluator.evaluatePredicate(null, scope);
        assertNull(result);
    }

    @Test
    void shouldEvaluateStringComparisonPredicate() {
        ExpressionScope scope = buildScope(
                Map.of("lead", Map.of("source", "Zillow")), "123", Map.of());

        Object result = evaluator.evaluatePredicate("event.payload.lead.source = 'Zillow'", scope);
        assertTrue((Boolean) result);
    }
}
