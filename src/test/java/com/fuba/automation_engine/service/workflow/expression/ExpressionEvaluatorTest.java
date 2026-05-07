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
        return buildScope(triggerPayload, sourceLeadId, Map.of(), stepOutputs);
    }

    private ExpressionScope buildScope(Map<String, Object> triggerPayload,
                                       String sourceLeadId,
                                       Map<String, Object> lead,
                                       Map<String, Map<String, Object>> stepOutputs) {
        RunContext runContext = new RunContext(null, triggerPayload, sourceLeadId, lead, stepOutputs);
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

    // ---- Phase 1 (agent-followup-enforcement): lead.* namespace ----

    @Test
    void shouldResolveLeadAssignedUserIdFromScope() {
        Map<String, Object> lead = Map.of(
                "assignedUserId", 30,
                "assignedTo", "ISA AuraKeyRealty");
        ExpressionScope scope = buildScope(Map.of(), "18399", lead, Map.of());

        Object result = evaluator.resolveTemplate("{{ lead.assignedUserId }}", scope);
        assertInstanceOf(Number.class, result);
        assertEquals(30, ((Number) result).intValue());
    }

    @Test
    void shouldResolveLeadAssignedToDisplayName() {
        Map<String, Object> lead = Map.of(
                "assignedUserId", 30,
                "assignedTo", "ISA AuraKeyRealty");
        ExpressionScope scope = buildScope(Map.of(), "18399", lead, Map.of());

        Object result = evaluator.resolveTemplate("{{ lead.assignedTo }}", scope);
        assertEquals("ISA AuraKeyRealty", result);
    }

    @Test
    void shouldResolveNestedLeadFields() {
        Map<String, Object> lead = Map.of(
                "phones", java.util.List.of(Map.of("value", "9059225917", "type", "mobile")));
        ExpressionScope scope = buildScope(Map.of(), "18399", lead, Map.of());

        Object result = evaluator.resolveTemplate("{{ lead.phones[0].value }}", scope);
        assertEquals("9059225917", result);
    }

    @Test
    void shouldReturnNullForMissingLeadFieldWithoutThrowing() {
        ExpressionScope scope = buildScope(Map.of(), "18399", Map.of(), Map.of());

        Object result = evaluator.resolveTemplate("{{ lead.assignedUserId }}", scope);
        assertNull(result);
    }

    @Test
    void shouldGracefullyHandleAbsentLeadSnapshotInBranchPredicate() {
        // Mirrors the branch_on_field use case: even when lead is empty, a
        // predicate that references lead.* should evaluate to a null/false-y
        // value rather than throwing.
        ExpressionScope scope = buildScope(Map.of(), "18399", Map.of(), Map.of());

        Object result = evaluator.evaluatePredicate("lead.assignedUserId > 0", scope);
        // dashjoin/jsonata returns null for missing-path comparisons; either null
        // or Boolean.FALSE is acceptable here — we only require no exception.
        assertTrue(result == null || Boolean.FALSE.equals(result),
                "Missing lead field comparison should be null/false, got: " + result);
    }
}
