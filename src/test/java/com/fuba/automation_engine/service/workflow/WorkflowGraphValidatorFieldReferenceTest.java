package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fuba.automation_engine.service.workflow.steps.DelayWorkflowStep;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WorkflowGraphValidator}'s Phase 1 (domain-events) addition:
 * workflow-creation-time validation that every {@code person.<field>} reference
 * in node config resolves against {@code PersonUpsertService.SNAPSHOT_FIELDS}.
 *
 * <p>The existing {@link WorkflowGraphValidatorTest} covers structural validation
 * (schema, nodes, transitions, cycles). This file is scoped specifically to the
 * field-reference check so the two concerns stay readable independently.
 */
class WorkflowGraphValidatorFieldReferenceTest {

    private WorkflowGraphValidator validator;

    /** Permissive-config step type so we can put arbitrary expressions/templates in config without triggering schema errors. */
    private static final WorkflowStepType ANY_CONFIG_STEP = new WorkflowStepType() {
        @Override public String id() { return "any_config_step"; }
        @Override public String displayName() { return "Any-config step (test)"; }
        @Override public String description() { return "Test step that accepts any config shape."; }
        @Override public Map<String, Object> configSchema() { return Map.of("type", "object"); }
        @Override public Set<String> declaredResultCodes() { return Set.of("DONE"); }
        @Override public StepExecutionResult execute(StepExecutionContext context) { return StepExecutionResult.success("DONE"); }
    };

    @BeforeEach
    void setUp() {
        WorkflowStepRegistry registry = new WorkflowStepRegistry(List.of(new DelayWorkflowStep(), ANY_CONFIG_STEP));
        validator = new WorkflowGraphValidator(registry);
    }

    @Test
    void acceptsKnownPersonFieldInJsonataExpression() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "$boolean(person.assignedUserId)"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void acceptsKnownPersonFieldInTemplate() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Hi {{ person.firstName }}, please call your person."));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void rejectsUnknownPersonFieldInExpression() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "$boolean(person.fakeField)"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid(), "Expected invalid for person.fakeField");
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("'person.fakeField'") && e.contains("node1")),
                "Expected error message to cite nodeId and field; got: " + result.errors());
    }

    @Test
    void rejectsUnknownPersonFieldInTemplate() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Owner: {{ person.notARealField }}"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid(), "Expected invalid for person.notARealField");
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("'person.notARealField'")),
                "Expected error to cite the unknown field; got: " + result.errors());
    }

    @Test
    void acceptsWorkflowWithNoPersonReferences() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "true",
                "resultMapping", Map.of("true", "DONE")));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void doesNotFalsePositiveOnSimilarSubstrings() {
        // 'personnel' contains 'person' but has no '.' after it.
        // 'salesperson.something' contains 'person.' but with a non-word-boundary prefix.
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Do not overwhelm personnel or salesperson.something"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void treatsNestedPersonReferenceAsTopLevelHead() {
        // `person.assignedUserId.foo` captures 'assignedUserId' (top-level, known) → passes.
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "person.assignedUserId.foo"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void agentFollowupEnforcementWorkflowPassesValidation() {
        // Regression: the production workflow that drove this whole feature must pass.
        // The expression below mirrors the EXACT gate string in
        // Docs/features/agent-followup-enforcement/workflow.json so that any future
        // change which drops `person.kind` from PersonUpsertService.capturedFieldNames()
        // (or otherwise breaks `person.kind` / `person.assignedUserId` / `person.assignedTo`
        // recognition) fails this test. If you change the production workflow's expression,
        // update this string in lockstep.
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "gate_assigned",
                "nodes", List.of(
                        Map.of(
                                "id", "gate_assigned",
                                "type", "any_config_step",
                                "config", Map.of(
                                        "expression",
                                        "$boolean(person.assignedUserId) and person.kind = \"LEAD\"",
                                        "resultMapping", Map.of("true", "DONE")),
                                "transitions", Map.of("DONE", List.of("nudge_note"))),
                        Map.of(
                                "id", "nudge_note",
                                "type", "any_config_step",
                                "config", Map.of(
                                        "mentionUserIds", List.of("{{ person.assignedUserId }}"),
                                        "mentionUserNames", List.of("{{ person.assignedTo }}"),
                                        "message", "Please call your person."),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void collectsMultipleUnknownFieldsAcrossOneNode() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "person.foo + person.bar"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        long unknownErrorCount = result.errors().stream()
                .filter(e -> e.contains("unknown person field"))
                .count();
        // 'foo' and 'bar' are both unknown — expect both reported.
        assertTrue(unknownErrorCount >= 2,
                "Expected at least 2 unknown-field errors; got: " + result.errors());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Builds a minimal single-node graph that uses {@code ANY_CONFIG_STEP} with the supplied config. */
    private static Map<String, Object> graphWithConfig(Map<String, Object> config) {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "node1",
                "nodes", List.of(
                        Map.of(
                                "id", "node1",
                                "type", "any_config_step",
                                "config", config,
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }
}
