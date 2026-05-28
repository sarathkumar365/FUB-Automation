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
                                       String sourcePersonId,
                                       Map<String, Map<String, Object>> stepOutputs) {
        return buildScope(triggerPayload, sourcePersonId, Map.of(), stepOutputs);
    }

    private ExpressionScope buildScope(Map<String, Object> triggerPayload,
                                       String sourcePersonId,
                                       Map<String, Object> person,
                                       Map<String, Map<String, Object>> stepOutputs) {
        return buildScope(triggerPayload, sourcePersonId, person, Map.of(), stepOutputs);
    }

    private ExpressionScope buildScope(Map<String, Object> triggerPayload,
                                       String sourcePersonId,
                                       Map<String, Object> person,
                                       Map<String, Object> now,
                                       Map<String, Map<String, Object>> stepOutputs) {
        RunContext runContext = new RunContext(null, triggerPayload, sourcePersonId, person, now, stepOutputs);
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
    void shouldResolveSourcePersonId() {
        ExpressionScope scope = buildScope(Map.of(), "7890", Map.of());

        Object result = evaluator.resolveTemplate("{{ sourcePersonId }}", scope);
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
                Map.of("person", Map.of("source", "Zillow")), "123", Map.of());

        Object result = evaluator.evaluatePredicate("event.payload.person.source = 'Zillow'", scope);
        assertTrue((Boolean) result);
    }

    // ---- Phase 1 (agent-followup-enforcement): person.* namespace ----

    @Test
    void shouldResolvePersonAssignedUserIdFromScope() {
        Map<String, Object> person = Map.of(
                "assignedUserId", 30,
                "assignedTo", "ISA AuraKeyRealty");
        ExpressionScope scope = buildScope(Map.of(), "18399", person, Map.of());

        Object result = evaluator.resolveTemplate("{{ person.assignedUserId }}", scope);
        assertInstanceOf(Number.class, result);
        assertEquals(30, ((Number) result).intValue());
    }

    @Test
    void shouldResolvePersonAssignedToDisplayName() {
        Map<String, Object> person = Map.of(
                "assignedUserId", 30,
                "assignedTo", "ISA AuraKeyRealty");
        ExpressionScope scope = buildScope(Map.of(), "18399", person, Map.of());

        Object result = evaluator.resolveTemplate("{{ person.assignedTo }}", scope);
        assertEquals("ISA AuraKeyRealty", result);
    }

    @Test
    void shouldResolveNestedPersonFields() {
        Map<String, Object> person = Map.of(
                "phones", java.util.List.of(Map.of("value", "9059225917", "type", "mobile")));
        ExpressionScope scope = buildScope(Map.of(), "18399", person, Map.of());

        Object result = evaluator.resolveTemplate("{{ person.phones[0].value }}", scope);
        assertEquals("9059225917", result);
    }

    @Test
    void shouldReturnNullForMissingPersonFieldWithoutThrowing() {
        ExpressionScope scope = buildScope(Map.of(), "18399", Map.of(), Map.of());

        Object result = evaluator.resolveTemplate("{{ person.assignedUserId }}", scope);
        assertNull(result);
    }

    @Test
    void shouldGracefullyHandleAbsentPersonSnapshotInBranchPredicate() {
        // Mirrors the branch_on_field use case: even when person is empty, a
        // predicate that references person.* should evaluate to a null/false-y
        // value rather than throwing.
        ExpressionScope scope = buildScope(Map.of(), "18399", Map.of(), Map.of());

        Object result = evaluator.evaluatePredicate("person.assignedUserId > 0", scope);
        // dashjoin/jsonata returns null for missing-path comparisons; either null
        // or Boolean.FALSE is acceptable here — we only require no exception.
        assertTrue(result == null || Boolean.FALSE.equals(result),
                "Missing person field comparison should be null/false, got: " + result);
    }

    // ---- Phase 3 (agent-followup-enforcement): now.* namespace ----

    @Test
    void shouldResolveNowIsDaytimeFromScope() {
        Map<String, Object> now = Map.of("isDaytime", true, "hourLocal", 14);
        ExpressionScope scope = buildScope(Map.of(), "1", Map.of(), now, Map.of());

        Object result = evaluator.resolveTemplate("{{ now.isDaytime }}", scope);
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void shouldResolveNowHourLocalAsNumber() {
        Map<String, Object> now = Map.of("isDaytime", true, "hourLocal", 14);
        ExpressionScope scope = buildScope(Map.of(), "1", Map.of(), now, Map.of());

        Object result = evaluator.resolveTemplate("{{ now.hourLocal }}", scope);
        assertInstanceOf(Number.class, result);
        assertEquals(14, ((Number) result).intValue());
    }

    @Test
    void shouldEvaluateNowIsDaytimeAsBranchPredicate() {
        // Replicates the agent-followup-enforcement 30-min branch_on_field shape.
        ExpressionScope daytimeScope = buildScope(
                Map.of(), "1", Map.of(), Map.of("isDaytime", true, "hourLocal", 14), Map.of());
        ExpressionScope offhoursScope = buildScope(
                Map.of(), "1", Map.of(), Map.of("isDaytime", false, "hourLocal", 22), Map.of());

        assertEquals(Boolean.TRUE, evaluator.evaluatePredicate("now.isDaytime", daytimeScope));
        assertEquals(Boolean.FALSE, evaluator.evaluatePredicate("now.isDaytime", offhoursScope));
    }

    @Test
    void shouldGracefullyHandleAbsentNowMap() {
        // Defensive: if buildRunContext somehow leaves now empty, expressions
        // reading now.* should not throw.
        ExpressionScope scope = buildScope(Map.of(), "1", Map.of(), Map.of(), Map.of());

        Object result = evaluator.resolveTemplate("{{ now.isDaytime }}", scope);
        assertNull(result);
    }
}
