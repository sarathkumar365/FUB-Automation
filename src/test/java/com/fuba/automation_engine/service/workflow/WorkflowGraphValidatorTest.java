package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fuba.automation_engine.service.workflow.steps.DelayWorkflowStep;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowGraphValidatorTest {

    private WorkflowGraphValidator validator;

    @BeforeEach
    void setUp() {
        WorkflowStepType delayStep = new DelayWorkflowStep();
        WorkflowStepType checkStep = new WorkflowStepType() {
            @Override public String id() { return "wait_and_check_claim"; }
            @Override public String displayName() { return "Check Claim"; }
            @Override public String description() { return "Test step"; }
            @Override public Map<String, Object> configSchema() { return Map.of("type", "object"); }
            @Override public Set<String> declaredResultCodes() { return Set.of("CLAIMED", "NOT_CLAIMED"); }
            @Override public StepExecutionResult execute(StepExecutionContext context) { return StepExecutionResult.success("CLAIMED"); }
        };
        WorkflowStepRegistry registry = new WorkflowStepRegistry(List.of(delayStep, checkStep));
        validator = new WorkflowGraphValidator(registry);
    }

    @Test
    void shouldAcceptValidSingleNodeGraph() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 5),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid but got errors: " + result.errors());
    }

    @Test
    void shouldAcceptValidLinearGraph() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", List.of("d2"))),
                        Map.of("id", "d2", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid but got errors: " + result.errors());
    }

    @Test
    void shouldAcceptValidFanOutGraph() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "check",
                "nodes", List.of(
                        Map.of("id", "check", "type", "wait_and_check_claim",
                                "config", Map.of(),
                                "transitions", Map.of(
                                        "CLAIMED", Map.of("terminal", "CLAIMED_DONE"),
                                        "NOT_CLAIMED", List.of("d1", "d2"))),
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE_1"))),
                        Map.of("id", "d2", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE_2")))));

        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.valid(), "Expected valid but got errors: " + result.errors());
    }

    @Test
    void shouldRejectNullGraph() {
        GraphValidationResult result = validator.validate(null);
        assertFalse(result.valid());
    }

    @Test
    void shouldRejectEmptyGraph() {
        GraphValidationResult result = validator.validate(Map.of());
        assertFalse(result.valid());
    }

    @Test
    void shouldRejectMissingEntryNode() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "nonexistent",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("nonexistent")));
    }

    @Test
    void shouldRejectUnknownStepType() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "unknown_step",
                                "config", Map.of(),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unknown_step")));
    }

    @Test
    void shouldRejectUndeclaredResultCode() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("BOGUS_CODE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("BOGUS_CODE")));
    }

    @Test
    void shouldRejectUnknownTransitionTarget() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", List.of("nonexistent")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("nonexistent")));
    }

    @Test
    void shouldRejectUnreachableNode() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE"))),
                        Map.of("id", "orphan", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("orphan")));
    }

    @Test
    void shouldRejectCycle() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", List.of("d2"))),
                        Map.of("id", "d2", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", List.of("d1")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("cycle")));
    }

    @Test
    void shouldRejectDuplicateNodeIds() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE"))),
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    void shouldRejectMissingRequiredConfig() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of(),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("delayMinutes")));
    }

    @Test
    void shouldRejectWrongSchemaVersion() {
        Map<String, Object> graph = Map.of(
                "schemaVersion", 99,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 1),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("schemaVersion")));
    }

    @Test
    void shouldReturnMultipleErrors() {
        // Use valid schemaVersion=1 so validation proceeds past the early-return gate
        // and collects errors from entryNode check + per-node type validation
        Map<String, Object> graph = Map.of(
                "schemaVersion", 1,
                "entryNode", "nonexistent",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "unknown_type",
                                "config", Map.of(),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE"))),
                        Map.of("id", "d2", "type", "also_unknown",
                                "config", Map.of(),
                                "transitions", Map.of("DONE", Map.of("terminal", "DONE")))));

        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.valid());
        // 1: entryNode 'nonexistent' not found, 2: d1 unknown type, 3: d2 unknown type
        assertEquals(3, result.errors().size());
    }
}
