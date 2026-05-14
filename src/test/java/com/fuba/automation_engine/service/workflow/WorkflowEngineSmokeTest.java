package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.WorkflowWorkerProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.LeadEntity;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowEngineSmokeTest {

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
    private LeadRepository leadRepository;

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
        leadRepository.deleteAll();
        worker = new WorkflowExecutionDueWorker(workerProperties, stepClaimRepository, stepExecutionService, clock);
    }

    @Test
    void shouldPlanAndExecuteSingleNodeWorkflow() {
        seedActiveWorkflow("SINGLE_DELAY", singleDelayGraph(0));

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest("SINGLE_DELAY", "TEST", "evt-1", null, "lead-1", null));

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());
        assertNotNull(planResult.runId());

        // Verify run and step were created
        WorkflowRunEntity run = runRepository.findById(planResult.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.PENDING, run.getStatus());

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(run.getId());
        assertEquals(1, steps.size());
        assertEquals(WorkflowRunStepStatus.PENDING, steps.getFirst().getStatus());
        assertEquals("d1", steps.getFirst().getNodeId());
        assertEquals("delay", steps.getFirst().getStepType());
        assertNotNull(steps.getFirst().getDueAt());

        // Execute via worker
        worker.pollAndProcessDueSteps();

        // Verify step completed and run completed
        WorkflowRunStepEntity completedStep = stepRepository.findByRunId(run.getId()).getFirst();
        assertEquals(WorkflowRunStepStatus.COMPLETED, completedStep.getStatus());
        assertEquals("DONE", completedStep.getResultCode());

        WorkflowRunEntity completedRun = runRepository.findById(run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, completedRun.getStatus());
        assertEquals("COMPLETED", completedRun.getReasonCode());
    }

    @Test
    void shouldPlanAndExecuteTwoNodeLinearWorkflow() {
        seedActiveWorkflow("TWO_DELAY", twoDelayLinearGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest("TWO_DELAY", "TEST", "evt-2", null, "lead-2", null));

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());
        Long runId = planResult.runId();

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(runId);
        assertEquals(2, steps.size());

        WorkflowRunStepEntity entryStep = steps.stream()
                .filter(s -> "d1".equals(s.getNodeId())).findFirst().orElseThrow();
        WorkflowRunStepEntity secondStep = steps.stream()
                .filter(s -> "d2".equals(s.getNodeId())).findFirst().orElseThrow();

        assertEquals(WorkflowRunStepStatus.PENDING, entryStep.getStatus());
        assertEquals(WorkflowRunStepStatus.WAITING_DEPENDENCY, secondStep.getStatus());
        assertEquals(1, secondStep.getPendingDependencyCount());
        assertNull(secondStep.getDueAt());

        // First poll: executes both due nodes within the same poll loop.
        worker.pollAndProcessDueSteps();

        entryStep = stepRepository.findById(entryStep.getId()).orElseThrow();
        secondStep = stepRepository.findById(secondStep.getId()).orElseThrow();
        assertEquals(WorkflowRunStepStatus.COMPLETED, entryStep.getStatus());
        assertEquals(WorkflowRunStepStatus.COMPLETED, secondStep.getStatus());
        assertEquals(0, secondStep.getPendingDependencyCount());
        assertNotNull(secondStep.getDueAt());

        WorkflowRunEntity midRun = runRepository.findById(runId).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, midRun.getStatus());
        assertEquals("DONE", secondStep.getResultCode());
        assertEquals("COMPLETED", midRun.getReasonCode());
    }

    @Test
    void shouldDeduplicateByIdempotencyKey() {
        seedActiveWorkflow("DEDUP_TEST", singleDelayGraph(0));

        WorkflowPlanRequest request = new WorkflowPlanRequest("DEDUP_TEST", "TEST", "evt-dup", null, "lead-1", null);

        WorkflowPlanningResult first = executionManager.plan(request);
        WorkflowPlanningResult second = executionManager.plan(request);

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, first.status());
        assertEquals(WorkflowPlanningResult.PlanningStatus.DUPLICATE_IGNORED, second.status());
        assertEquals(first.runId(), second.runId());
        assertEquals(1, runRepository.count());
    }

    @Test
    void shouldNotDeduplicateAcrossCaseDistinctWorkflowKeys() {
        seedActiveWorkflow("CaseFlow", singleDelayGraph(0));
        seedActiveWorkflow("caseflow", singleDelayGraph(0));

        WorkflowPlanRequest firstRequest =
                new WorkflowPlanRequest("CaseFlow", "TEST", "evt-case", null, "lead-1", null);
        WorkflowPlanRequest secondRequest =
                new WorkflowPlanRequest("caseflow", "TEST", "evt-case", null, "lead-1", null);

        WorkflowPlanningResult first = executionManager.plan(firstRequest);
        WorkflowPlanningResult second = executionManager.plan(secondRequest);

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, first.status());
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, second.status());
        assertEquals(2, runRepository.count());
    }

    @Test
    void shouldReturnBlockedWhenWorkflowNotFound() {
        WorkflowPlanningResult result = executionManager.plan(
                new WorkflowPlanRequest("NONEXISTENT", "TEST", "evt-3", null, "lead-1", null));

        assertEquals(WorkflowPlanningResult.PlanningStatus.BLOCKED, result.status());
        assertNull(result.runId());
    }

    @Test
    void shouldMaterializeDelayDueAtCorrectly() {
        seedActiveWorkflow("DELAY_5", singleDelayGraph(5));

        WorkflowPlanningResult result = executionManager.plan(
                new WorkflowPlanRequest("DELAY_5", "TEST", "evt-4", null, "lead-1", null));

        WorkflowRunStepEntity step = stepRepository.findByRunId(result.runId()).getFirst();
        // Clock is fixed at 2026-01-01T12:00:00Z, delay is 5 minutes
        assertTrue(step.getDueAt().isAfter(Instant.parse("2026-01-01T12:04:59Z").atOffset(ZoneOffset.UTC)));
        assertTrue(step.getDueAt().isBefore(Instant.parse("2026-01-01T12:05:01Z").atOffset(ZoneOffset.UTC)));
    }

    @Test
    void shouldHandleTerminalTransitionFromEntryNode() {
        // Graph where the only node terminates immediately
        seedActiveWorkflow("TERMINAL_ENTRY", singleDelayGraph(0));

        WorkflowPlanningResult result = executionManager.plan(
                new WorkflowPlanRequest("TERMINAL_ENTRY", "TEST", "evt-5", null, "lead-1", null));

        worker.pollAndProcessDueSteps();

        WorkflowRunEntity run = runRepository.findById(result.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
    }

    private void seedActiveWorkflow(String key, Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("Test Workflow " + key);
        entity.setGraph(graph);
        entity.setStatus(WorkflowStatus.ACTIVE);
        workflowRepository.saveAndFlush(entity);
    }

    private Map<String, Object> singleDelayGraph(int delayMinutes) {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", delayMinutes),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    @Test
    void shouldExposeLeadNamespaceFromLocalSnapshotInBranchOnFieldExpression() {
        // Phase 1 (agent-followup-enforcement) end-to-end check:
        // seed a LeadEntity locally, plan a workflow whose entry node is a
        // branch_on_field referencing {{ lead.assignedUserId }}, run it, and
        // assert the JSONata expression saw the snapshotted value (no FUB
        // call) and routed the workflow accordingly.
        seedLeadSnapshot("lead-phase1-match", 30, "ISA AuraKeyRealty");
        seedActiveWorkflow("LEAD_NAMESPACE_E2E", branchOnLeadAssignedUserGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "LEAD_NAMESPACE_E2E", "TEST", "evt-lead-1", null, "lead-phase1-match", null));

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());
        assertNotNull(planResult.runId());

        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity step = stepRepository.findByRunId(planResult.runId()).getFirst();
        assertEquals(WorkflowRunStepStatus.COMPLETED, step.getStatus());
        assertEquals("MATCHED", step.getResultCode(),
                "branch_on_field should have read lead.assignedUserId=30 from the local snapshot and matched");

        WorkflowRunEntity completedRun = runRepository.findById(planResult.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, completedRun.getStatus());
        assertEquals("MATCHED", completedRun.getReasonCode());
    }

    @Test
    void shouldExposeNowIsDaytimeFromBusinessHoursServiceInBranchOnFieldExpression() {
        // Phase 3 (agent-followup-enforcement) end-to-end check: the engine's
        // fixed test clock is 2026-01-01 12:00 UTC (= 07:00 Toronto local) on
        // a Thursday. With default business hours (9-18 weekdays), 07:00 is
        // OFF-hours, so the workflow should route to the OFFHOURS branch.
        seedActiveWorkflow("NOW_NS_E2E_OFFHOURS", branchOnNowIsDaytimeGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "NOW_NS_E2E_OFFHOURS", "TEST", "evt-now-1", null, "lead-now-1", null));
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());

        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity step = stepRepository.findByRunId(planResult.runId()).getFirst();
        assertEquals(WorkflowRunStepStatus.COMPLETED, step.getStatus());
        assertEquals("OFFHOURS", step.getResultCode(),
                "07:00 Toronto local should route to OFFHOURS branch");
    }

    private Map<String, Object> branchOnNowIsDaytimeGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "check",
                "nodes", List.of(
                        Map.of("id", "check", "type", "branch_on_field",
                                "config", Map.of(
                                        "expression", "now.isDaytime",
                                        "resultMapping", Map.of("true", "DAYTIME", "false", "OFFHOURS"),
                                        "defaultResultCode", "OFFHOURS"),
                                "transitions", Map.of(
                                        "DAYTIME", Map.of("terminal", "DAYTIME"),
                                        "OFFHOURS", Map.of("terminal", "OFFHOURS")))));
    }

    @Test
    void shouldGracefullyHandleMissingLeadSnapshotInBranchOnFieldExpression() {
        // No lead seeded — lead.* should still be present as an empty map; the
        // branch_on_field default branch should fire instead of throwing.
        seedActiveWorkflow("LEAD_NAMESPACE_MISSING", branchOnLeadAssignedUserGraph());

        WorkflowPlanningResult planResult = executionManager.plan(
                new WorkflowPlanRequest(
                        "LEAD_NAMESPACE_MISSING", "TEST", "evt-lead-2", null, "lead-not-ingested", null));

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, planResult.status());

        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity step = stepRepository.findByRunId(planResult.runId()).getFirst();
        assertEquals(WorkflowRunStepStatus.COMPLETED, step.getStatus());
        assertEquals("MISSED", step.getResultCode(),
                "Missing lead snapshot should fall through to defaultResultCode without throwing");
    }

    private void seedLeadSnapshot(String sourceLeadId, int assignedUserId, String assignedTo) {
        ObjectNode leadDetails = objectMapper.createObjectNode();
        leadDetails.put("assignedUserId", assignedUserId);
        leadDetails.put("assignedTo", assignedTo);

        LeadEntity entity = new LeadEntity();
        entity.setSourceSystem("FUB");
        entity.setSourceLeadId(sourceLeadId);
        entity.setStatus(LeadStatus.ACTIVE);
        entity.setLeadDetails(leadDetails);
        OffsetDateTime now = OffsetDateTime.now(clock);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastSyncedAt(now);
        leadRepository.saveAndFlush(entity);
    }

    private Map<String, Object> branchOnLeadAssignedUserGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "check",
                "nodes", List.of(
                        Map.of("id", "check", "type", "branch_on_field",
                                "config", Map.of(
                                        "expression", "lead.assignedUserId",
                                        "resultMapping", Map.of("30", "MATCHED"),
                                        "defaultResultCode", "MISSED"),
                                "transitions", Map.of(
                                        "MATCHED", Map.of("terminal", "MATCHED"),
                                        "MISSED", Map.of("terminal", "MISSED")))));
    }

    private Map<String, Object> twoDelayLinearGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of("id", "d1", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", List.of("d2"))),
                        Map.of("id", "d2", "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }
}
