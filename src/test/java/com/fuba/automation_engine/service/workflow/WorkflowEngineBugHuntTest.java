package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.WorkflowWorkerProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fresh-eyes bug-hunt suite. Each test asserts the engine's CORRECT expected behavior.
 * Tests that currently fail because of a confirmed defect are isolated with
 * {@code @Disabled("BUG-NN: ...")} and tagged {@code "bug"} so the main build stays green
 * while preserving an executable reproduction. See
 * {@code Docs/audits/test-engineer-findings-2026-05-31.md}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowEngineBugHuntTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("automation_engine")
            .withUsername("automation")
            .withPassword("automation");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("workflow.worker.enabled", () -> "false");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private AutomationWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowRunRepository runRepository;

    @Autowired
    private WorkflowRunStepRepository stepRepository;

    @Autowired
    private WorkflowExecutionManager executionManager;

    @Autowired
    private WorkflowStepExecutionService stepExecutionService;

    @Autowired
    private WorkflowWorkerProperties workerProperties;

    @Autowired
    private WorkflowRunStepClaimRepository stepClaimRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private WorkflowExecutionDueWorker worker;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        workflowRepository.deleteAll();
        personRepository.deleteAll();
        worker = new WorkflowExecutionDueWorker(workerProperties, stepClaimRepository, stepExecutionService, clock);
    }

    /**
     * BUG-01 — Conditional branch + join deadlocks the run forever.
     *
     * <p>Graph: a {@code branch_on_field} entry routes to EITHER {@code leftAction} or
     * {@code rightAction}; both converge on a single {@code join} node, which terminates.
     *
     * <p>{@code buildPredecessorMap} statically counts every incoming edge, so {@code join}
     * is materialized with {@code pendingDependencyCount = 2}. At runtime the branch fires
     * only ONE path, so {@code activateNextNodes} decrements the join exactly once (2 -> 1).
     * The join never reaches 0 and stays {@code WAITING_DEPENDENCY}; the unselected branch
     * stays {@code WAITING_DEPENDENCY} too. {@code checkRunCompletion} sees non-terminal steps
     * and never finalizes — the run hangs {@code PENDING} indefinitely with no claimable steps.
     *
     * <p>Expected correct behavior: the run reaches a terminal state (COMPLETED). This test
     * is disabled because it currently fails on that assertion, proving the deadlock.
     */
    @Test
    @Tag("bug")
    @Disabled("BUG-01: conditional branch+join deadlocks — join pendingDependencyCount counts "
            + "all static predecessors but only one branch fires at runtime; run hangs PENDING forever")
    void conditionalBranchJoinShouldReachTerminalState() {
        seedPersonSnapshot("person-bug01", 30);
        seedActiveWorkflow("BUG01_CONDITIONAL_JOIN", conditionalJoinGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "BUG01_CONDITIONAL_JOIN", "TEST", "evt-bug01", null, "person-bug01", null));
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());
        Long runId = planResult.runId();

        // Drive the worker to quiescence: poll until no further progress is possible.
        drivePolls(8);

        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();
        assertNotEquals(WorkflowRunStatus.PENDING, run.getStatus(),
                "Run must not hang in PENDING — a conditional branch+join should still complete");
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus(),
                "The selected path (branch -> leftAction -> join -> terminal) should complete the run");
    }

    /**
     * Companion characterization test (kept GREEN): documents the CURRENT buggy reality so the
     * deadlock is visible in a runnable form without breaking the build. If BUG-01 is ever fixed,
     * this test will start failing and should be deleted in favor of the assertion above.
     */
    @Test
    @Tag("bug")
    void conditionalBranchJoinCurrentlyDeadlocksPending() {
        seedPersonSnapshot("person-bug01c", 30);
        seedActiveWorkflow("BUG01_CONDITIONAL_JOIN_CHAR", conditionalJoinGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "BUG01_CONDITIONAL_JOIN_CHAR", "TEST", "evt-bug01c", null, "person-bug01c", null));
        Long runId = planResult.runId();

        drivePolls(8);

        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();
        // Current (defective) behavior: run never finalizes.
        assertEquals(WorkflowRunStatus.PENDING, run.getStatus(),
                "DOCUMENTING BUG-01: the run is stuck PENDING after the deadlock");

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(runId);
        WorkflowRunStepEntity join = steps.stream()
                .filter(s -> "join".equals(s.getNodeId())).findFirst().orElseThrow();
        WorkflowRunStepEntity rightAction = steps.stream()
                .filter(s -> "rightAction".equals(s.getNodeId())).findFirst().orElseThrow();
        assertEquals(WorkflowRunStepStatus.WAITING_DEPENDENCY, join.getStatus(),
                "join is stranded waiting on a predecessor that will never fire");
        assertEquals(1, join.getPendingDependencyCount(),
                "join decremented once (only the taken branch fired) and never reaches 0");
        assertEquals(WorkflowRunStepStatus.WAITING_DEPENDENCY, rightAction.getStatus(),
                "the unselected branch is stranded WAITING_DEPENDENCY forever");
    }

    /**
     * Positive control (kept GREEN): the SAME diamond shape, but with an UNCONDITIONAL fan-out
     * entry (delay -> [B, C]) so BOTH predecessors of the join actually fire. The join's
     * pendingDependencyCount (2) is correctly decremented twice and the run completes.
     *
     * <p>This isolates BUG-01: joins work when every counted predecessor executes; the defect is
     * specifically a CONDITIONAL branch where only one predecessor ever fires.
     */
    @Test
    void unconditionalFanOutWithJoinCompletes() {
        seedActiveWorkflow("CONTROL_FANOUT_JOIN", unconditionalFanOutJoinGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "CONTROL_FANOUT_JOIN", "TEST", "evt-control", null, "person-control", null));
        Long runId = planResult.runId();

        drivePolls(8);

        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus(),
                "An unconditional fan-out + join must complete: both predecessors fire, join reaches 0");
        for (WorkflowRunStepEntity step : stepRepository.findByRunId(runId)) {
            assertEquals(WorkflowRunStepStatus.COMPLETED, step.getStatus(),
                    "every node should have executed: " + step.getNodeId());
        }
    }

    /**
     * BUG-02 — Invalid JSONata in {@code branch_on_field} silently routes to the default branch.
     *
     * <p>{@code JsonataExpressionEvaluator.evaluateExpression} catches ALL exceptions and returns
     * {@code null} (known-issue #10). In a branch step the null becomes the string "null", which
     * is absent from {@code resultMapping}, so {@code routeResult} falls through to
     * {@code defaultResultCode}. The step reports SUCCESS on the default branch. The
     * {@code EXPRESSION_EVAL_ERROR} failure path (BranchOnFieldWorkflowStep:137) is therefore
     * UNREACHABLE for malformed expressions — a typo in a routing predicate is invisible.
     *
     * <p>Expected correct behavior: a broken expression should FAIL the step loudly, not quietly
     * pick the default route. Disabled because it currently fails that assertion.
     */
    @Test
    @Tag("bug")
    @Disabled("BUG-02: invalid branch_on_field expression degrades to null and silently routes to "
            + "defaultResultCode instead of failing with EXPRESSION_EVAL_ERROR (extends known-issue #10)")
    void invalidBranchExpressionShouldFailLoudly() {
        seedPersonSnapshot("person-bug02", 30);
        seedActiveWorkflow("BUG02_BAD_EXPR", invalidExpressionBranchGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest("BUG02_BAD_EXPR", "TEST", "evt-bug02", null, "person-bug02", null));

        drivePolls(2);

        WorkflowRunStepEntity step = stepRepository.findByRunId(planResult.runId()).getFirst();
        assertEquals(WorkflowRunStepStatus.FAILED, step.getStatus(),
                "A malformed routing expression must not be treated as a successful default route");
        assertEquals("EXPRESSION_EVAL_ERROR", step.getResultCode(),
                "branch_on_field should surface EXPRESSION_EVAL_ERROR for invalid JSONata");
    }

    /**
     * Companion characterization (kept GREEN): documents that an invalid expression currently
     * routes to the default branch and reports success.
     */
    @Test
    @Tag("bug")
    void invalidBranchExpressionCurrentlyRoutesToDefault() {
        seedPersonSnapshot("person-bug02c", 30);
        seedActiveWorkflow("BUG02_BAD_EXPR_CHAR", invalidExpressionBranchGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest("BUG02_BAD_EXPR_CHAR", "TEST", "evt-bug02c", null, "person-bug02c", null));

        drivePolls(2);

        WorkflowRunStepEntity step = stepRepository.findByRunId(planResult.runId()).getFirst();
        assertEquals(WorkflowRunStepStatus.COMPLETED, step.getStatus(),
                "DOCUMENTING BUG-02: broken expression yields a 'successful' step");
        assertEquals("FALLBACK", step.getResultCode(),
                "DOCUMENTING BUG-02: broken expression silently took defaultResultCode");
    }

    /**
     * BUG-03 — A terminal transition in one parallel branch SKIPS not-yet-due sibling branches.
     *
     * <p>Graph: entry fans out to {@code fast} (due now, terminates the run) and {@code slow}
     * (delayed, terminates the run). When {@code fast} executes, {@code applyTerminalTransition}
     * finalizes the whole run and marks EVERY remaining WAITING_DEPENDENCY/PENDING step SKIPPED —
     * including {@code slow}, whose action never runs even though it is an independent parallel
     * branch. Work is silently dropped.
     *
     * <p>This contradicts the join model (pendingDependencyCount implies parallel branches are
     * meant to run concurrently). Kept GREEN as a characterization: it asserts the CURRENT skip so
     * the hazard is visible and reproducible. Correct behavior would let {@code slow} run.
     */
    @Test
    @Tag("bug")
    void parallelTerminalSilentlySkipsSlowerSibling() {
        seedActiveWorkflow("BUG03_PARALLEL_TERMINAL", parallelTerminalRaceGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "BUG03_PARALLEL_TERMINAL", "TEST", "evt-bug03", null, "person-bug03", null));
        Long runId = planResult.runId();

        drivePolls(8);

        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();
        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(runId);
        WorkflowRunStepEntity fast = steps.stream()
                .filter(s -> "fast".equals(s.getNodeId())).findFirst().orElseThrow();
        WorkflowRunStepEntity slow = steps.stream()
                .filter(s -> "slow".equals(s.getNodeId())).findFirst().orElseThrow();

        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals(WorkflowRunStepStatus.COMPLETED, fast.getStatus(),
                "the fast branch terminated the run");
        assertEquals(WorkflowRunStepStatus.SKIPPED, slow.getStatus(),
                "DOCUMENTING BUG-03: the slower independent parallel branch was silently skipped");
    }

    /**
     * BUG-04 — No-eventId triggers for the same (workflow, source, person) collapse to one run.
     *
     * <p>{@code buildIdempotencyKey} uses a literal {@code FALLBACK|NO_EVENT} segment when
     * {@code eventId} is blank, so two genuinely distinct triggers that lack an eventId hash to the
     * same idempotency key. The second is reported {@code DUPLICATE_IGNORED} and never runs.
     *
     * <p>Kept GREEN as a characterization (this may be intentional dedup — flagged for intent
     * confirmation in the findings report). It documents that distinctness via {@code triggerPayload}
     * alone is NOT enough to avoid collapse when eventId is absent.
     */
    @Test
    @Tag("bug")
    void noEventIdTriggersCollapseToSingleRun() {
        seedActiveWorkflow("BUG04_FALLBACK_DEDUP", singleNodeGraph());

        WorkflowPlanRequest firstTrigger =
                new WorkflowPlanRequest("BUG04_FALLBACK_DEDUP", "TEST", null, null, "person-bug04", null);
        WorkflowPlanRequest secondDistinctTrigger =
                new WorkflowPlanRequest("BUG04_FALLBACK_DEDUP", "TEST", null, null, "person-bug04", null);

        WorkflowPlanningResult first = executionManager.plan(firstTrigger);
        WorkflowPlanningResult second = executionManager.plan(secondDistinctTrigger);

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, first.status());
        assertEquals(WorkflowPlanningResult.PlanningStatus.DUPLICATE_IGNORED, second.status(),
                "DOCUMENTING BUG-04: a second eventId-less trigger for the same person collapses");
        assertEquals(first.runId(), second.runId());
        assertEquals(1, runRepository.count(),
                "only one run materialized despite two distinct trigger occurrences");
    }

    // Poll the worker repeatedly; each poll executes all currently-due PENDING steps.
    private void drivePolls(int times) {
        for (int i = 0; i < times; i++) {
            worker.pollAndProcessDueSteps();
        }
    }

    private void seedActiveWorkflow(String key, Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("BugHunt Workflow " + key);
        entity.setGraph(graph);
        entity.setStatus(WorkflowStatus.ACTIVE);
        workflowRepository.saveAndFlush(entity);
    }

    private void seedPersonSnapshot(String sourcePersonId, int assignedUserId) {
        ObjectNode personDetails = objectMapper.createObjectNode();
        personDetails.put("assignedUserId", assignedUserId);

        PersonEntity entity = new PersonEntity();
        entity.setSourceSystem("FUB");
        entity.setSourcePersonId(sourcePersonId);
        entity.setStatus(PersonStatus.ACTIVE);
        entity.setPersonDetails(personDetails);
        OffsetDateTime now = OffsetDateTime.now(clock);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(now);
        personRepository.saveAndFlush(entity);
    }

    private Map<String, Object> singleNodeGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    // entry fans out unconditionally to B and C; both feed `join` -> terminal.
    private Map<String, Object> unconditionalFanOutJoinGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "a",
                "nodes", List.of(
                        Map.of("id", "a", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("b", "c"))),
                        Map.of("id", "b", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("join"))),
                        Map.of("id", "c", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("join"))),
                        Map.of("id", "join", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    // entry branch_on_field with a syntactically invalid JSONata expression.
    private Map<String, Object> invalidExpressionBranchGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "branch",
                "nodes", List.of(
                        Map.of("id", "branch", "type", "branch_on_field",
                                "config", Map.of(
                                        "expression", "person.assignedUserId(((",
                                        "resultMapping", Map.of("30", "MATCHED"),
                                        "defaultResultCode", "FALLBACK"),
                                "transitions", Map.of(
                                        "MATCHED", Map.of("terminal", "MATCHED"),
                                        "FALLBACK", Map.of("terminal", "FALLBACK")))));
    }

    // entry fans out to a due `fast` (terminates run) and a delayed `slow` (also terminal).
    private Map<String, Object> parallelTerminalRaceGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "a",
                "nodes", List.of(
                        Map.of("id", "a", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("fast", "slow"))),
                        Map.of("id", "fast", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "FAST_DONE"))),
                        Map.of("id", "slow", "type", "delay",
                                "config", Map.of("delayMinutes", 30),
                                "transitions", Map.of("DONE", Map.of("terminal", "SLOW_DONE")))));
    }

    // entry branch routes LEFT (assignedUserId=30) or RIGHT; both converge on `join` -> terminal.
    private Map<String, Object> conditionalJoinGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "branch",
                "nodes", List.of(
                        Map.of("id", "branch", "type", "branch_on_field",
                                "config", Map.of(
                                        "expression", "person.assignedUserId",
                                        "resultMapping", Map.of("30", "LEFT"),
                                        "defaultResultCode", "RIGHT"),
                                "transitions", Map.of(
                                        "LEFT", List.of("leftAction"),
                                        "RIGHT", List.of("rightAction"))),
                        Map.of("id", "leftAction", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("join"))),
                        Map.of("id", "rightAction", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("join"))),
                        Map.of("id", "join", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }
}
