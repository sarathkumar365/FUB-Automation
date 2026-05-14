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
 * workflow-creation-time validation that every {@code lead.<field>} reference
 * in node config resolves against {@code LeadUpsertService.SNAPSHOT_FIELDS}.
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
    void acceptsKnownLeadFieldInJsonataExpression() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "$boolean(lead.assignedUserId)"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void acceptsKnownLeadFieldInTemplate() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Hi {{ lead.firstName }}, please call your lead."));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void rejectsUnknownLeadFieldInExpression() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "$boolean(lead.fakeField)"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid(), "Expected invalid for lead.fakeField");
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("'lead.fakeField'") && e.contains("node1")),
                "Expected error message to cite nodeId and field; got: " + result.errors());
    }

    @Test
    void rejectsUnknownLeadFieldInTemplate() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Owner: {{ lead.notARealField }}"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid(), "Expected invalid for lead.notARealField");
        assertTrue(
                result.errors().stream().anyMatch(e -> e.contains("'lead.notARealField'")),
                "Expected error to cite the unknown field; got: " + result.errors());
    }

    @Test
    void acceptsWorkflowWithNoLeadReferences() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "true",
                "resultMapping", Map.of("true", "DONE")));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void doesNotFalsePositiveOnSimilarSubstrings() {
        // 'mislead' contains 'lead' but is not a `lead.X` reference.
        // 'plead.something' contains 'lead.' but with a non-word-boundary prefix.
        Map<String, Object> graph = graphWithConfig(Map.of(
                "message", "Do not mislead the agent or plead.something"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void treatsNestedLeadReferenceAsTopLevelHead() {
        // `lead.assignedUserId.foo` captures 'assignedUserId' (top-level, known) → passes.
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "lead.assignedUserId.foo"));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void agentFollowupEnforcementWorkflowPassesValidation() {
        // Regression: the production workflow that drove this whole feature must pass.
        // Same field references as Docs/features/agent-followup-enforcement/workflow.json
        // (lead.assignedUserId, lead.assignedTo).
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "gate_assigned",
                "nodes", List.of(
                        Map.of(
                                "id", "gate_assigned",
                                "type", "any_config_step",
                                "config", Map.of(
                                        "expression", "$boolean(lead.assignedUserId)",
                                        "resultMapping", Map.of("true", "DONE")),
                                "transitions", Map.of("DONE", List.of("nudge_note"))),
                        Map.of(
                                "id", "nudge_note",
                                "type", "any_config_step",
                                "config", Map.of(
                                        "mentionUserIds", List.of("{{ lead.assignedUserId }}"),
                                        "mentionUserNames", List.of("{{ lead.assignedTo }}"),
                                        "message", "Please call your lead."),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid; errors: " + result.errors());
    }

    @Test
    void collectsMultipleUnknownFieldsAcrossOneNode() {
        Map<String, Object> graph = graphWithConfig(Map.of(
                "expression", "lead.foo + lead.bar"));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        long unknownErrorCount = result.errors().stream()
                .filter(e -> e.contains("unknown lead field"))
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
