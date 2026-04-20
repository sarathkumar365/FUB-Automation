package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.config.WorkflowWorkerProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
class WorkflowParityTest {

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

        @Bean
        @Primary
        StubFollowUpBossClient stubFollowUpBossClient() {
            return new StubFollowUpBossClient();
        }
    }

    @Autowired private AutomationWorkflowRepository workflowRepository;
    @Autowired private WorkflowRunRepository runRepository;
    @Autowired private WorkflowRunStepRepository stepRepository;
    @Autowired private WorkflowExecutionManager executionManager;
    @Autowired private WorkflowStepExecutionService stepExecutionService;
    @Autowired private WorkflowWorkerProperties workerProperties;
    @Autowired private WorkflowRunStepClaimRepository stepClaimRepository;
    @Autowired private ProcessedCallRepository processedCallRepository;
    @Autowired private Clock clock;
    @Autowired private StubFollowUpBossClient stubClient;
    private WorkflowExecutionDueWorker worker;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        processedCallRepository.deleteAll();
        workflowRepository.deleteAll();
        stubClient.reset();
        worker = new WorkflowExecutionDueWorker(workerProperties, stepClaimRepository, stepExecutionService, clock);
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 1: Lead is claimed → terminal COMPLIANT_CLOSED
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldTerminateWhenLeadIsClaimed() {
        seedParityWorkflow();
        stubClient.setPersonDetails(7890L, new PersonDetails(7890L, true, 42L, 0));

        WorkflowPlanningResult plan = planParityRun("7890", "evt-1");
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, plan.status());

        // Execute wait_claim
        worker.pollAndProcessDueSteps();

        WorkflowRunEntity run = runRepository.findById(plan.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals("COMPLIANT_CLOSED", run.getReasonCode());

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(run.getId());
        WorkflowRunStepEntity claimStep = findStepByNodeId(steps, "wait_claim");
        WorkflowRunStepEntity commStep = findStepByNodeId(steps, "wait_comm");
        WorkflowRunStepEntity reassignStep = findStepByNodeId(steps, "do_reassign");

        assertEquals(WorkflowRunStepStatus.COMPLETED, claimStep.getStatus());
        assertEquals("CLAIMED", claimStep.getResultCode());
        assertEquals(WorkflowRunStepStatus.SKIPPED, commStep.getStatus());
        assertEquals(WorkflowRunStepStatus.SKIPPED, reassignStep.getStatus());
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 2: Lead unclaimed, communication found → COMPLIANT_CLOSED
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldTerminateWhenCommunicationFound() {
        seedParityWorkflow();
        stubClient.setPersonDetails(7890L, new PersonDetails(7890L, false, null, 0));
        saveLocalCallEvidence(8201L, "7890", true, 64, "Connected", 3);

        WorkflowPlanningResult plan = planParityRun("7890", "evt-2");

        // Execute wait_claim → NOT_CLAIMED → activates wait_comm
        worker.pollAndProcessDueSteps();
        // Execute wait_comm → CONVERSATIONAL → terminal
        worker.pollAndProcessDueSteps();

        WorkflowRunEntity run = runRepository.findById(plan.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals("COMPLIANT_CLOSED", run.getReasonCode());

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(run.getId());
        assertEquals(WorkflowRunStepStatus.COMPLETED, findStepByNodeId(steps, "wait_claim").getStatus());
        assertEquals("NOT_CLAIMED", findStepByNodeId(steps, "wait_claim").getResultCode());
        assertEquals(WorkflowRunStepStatus.COMPLETED, findStepByNodeId(steps, "wait_comm").getStatus());
        assertEquals("CONVERSATIONAL", findStepByNodeId(steps, "wait_comm").getResultCode());
        assertEquals(WorkflowRunStepStatus.SKIPPED, findStepByNodeId(steps, "do_reassign").getStatus());
        assertEquals(0, stubClient.commCheckCalls.size(), "Local evidence path should not call fallback communication API");
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 3: Unclaimed, no communication, reassign succeeds → ACTION_COMPLETED
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldReassignWhenNoCommunicationFound() {
        seedParityWorkflow();
        stubClient.setPersonDetails(7890L, new PersonDetails(7890L, false, null, 0));
        saveOutboundCallEvidence(8202L, "7890", 12, "Connected", 2);
        stubClient.setReassignResult(ActionExecutionResult.ok());

        WorkflowPlanningResult plan = planParityRun("7890", "evt-3");

        // Execute wait_claim → NOT_CLAIMED
        worker.pollAndProcessDueSteps();
        // Execute wait_comm → CONNECTED_NON_CONVERSATIONAL
        worker.pollAndProcessDueSteps();
        // Execute do_reassign → SUCCESS
        worker.pollAndProcessDueSteps();

        WorkflowRunEntity run = runRepository.findById(plan.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals("ACTION_COMPLETED", run.getReasonCode());

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(run.getId());
        assertEquals(WorkflowRunStepStatus.COMPLETED, findStepByNodeId(steps, "wait_claim").getStatus());
        assertEquals(WorkflowRunStepStatus.COMPLETED, findStepByNodeId(steps, "wait_comm").getStatus());
        assertEquals("CONNECTED_NON_CONVERSATIONAL", findStepByNodeId(steps, "wait_comm").getResultCode());
        assertEquals(WorkflowRunStepStatus.COMPLETED, findStepByNodeId(steps, "do_reassign").getStatus());
        assertEquals("SUCCESS", findStepByNodeId(steps, "do_reassign").getResultCode());

        // Verify reassignPerson was called with the right args (personId=7890, targetUserId=77 from trigger payload)
        assertEquals(1, stubClient.reassignCalls.size());
        assertEquals(7890L, stubClient.reassignCalls.getFirst()[0]);
        assertEquals(77L, stubClient.reassignCalls.getFirst()[1]);
        assertEquals(0, stubClient.commCheckCalls.size(), "Local evidence path should not call fallback communication API");
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 4: Unclaimed, no communication, reassign fails → ACTION_FAILED
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldHandleReassignFailure() {
        seedParityWorkflow();
        stubClient.setPersonDetails(7890L, new PersonDetails(7890L, false, null, 0));
        saveOutboundCallEvidence(8203L, "7890", 0, "No Answer", 1);
        stubClient.setReassignResult(ActionExecutionResult.failure("FUB_ERROR", "Person update failed"));

        WorkflowPlanningResult plan = planParityRun("7890", "evt-4");

        worker.pollAndProcessDueSteps(); // wait_claim
        worker.pollAndProcessDueSteps(); // wait_comm
        worker.pollAndProcessDueSteps(); // do_reassign → FAILED

        WorkflowRunEntity run = runRepository.findById(plan.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.FAILED, run.getStatus());
        assertEquals("FAILED", run.getReasonCode());

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(run.getId());
        assertEquals("COMM_NOT_FOUND", findStepByNodeId(steps, "wait_comm").getResultCode());
        assertEquals(WorkflowRunStepStatus.FAILED, findStepByNodeId(steps, "do_reassign").getStatus());
        assertNull(findStepByNodeId(steps, "do_reassign").getResultCode());
        assertEquals(0, stubClient.commCheckCalls.size(), "Local evidence path should not call fallback communication API");
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 5: Template resolution audit trail
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldPersistResolvedConfigWithTemplateValues() {
        seedParityWorkflow();
        stubClient.setPersonDetails(7890L, new PersonDetails(7890L, false, null, 0));
        saveOutboundCallEvidence(8204L, "7890", 8, "Connected", 4);
        stubClient.setReassignResult(ActionExecutionResult.ok());

        WorkflowPlanningResult plan = planParityRun("7890", "evt-5");

        worker.pollAndProcessDueSteps(); // wait_claim
        worker.pollAndProcessDueSteps(); // wait_comm
        worker.pollAndProcessDueSteps(); // do_reassign

        List<WorkflowRunStepEntity> steps = stepRepository.findByRunId(plan.runId());
        WorkflowRunStepEntity reassignStep = findStepByNodeId(steps, "do_reassign");

        // Verify resolved config has numeric targetUserId (not the template string)
        assertNotNull(reassignStep.getResolvedConfig());
        Object resolvedTargetUserId = reassignStep.getResolvedConfig().get("targetUserId");
        assertNotNull(resolvedTargetUserId, "resolvedConfig should contain targetUserId");
        assertTrue(resolvedTargetUserId instanceof Number,
                "targetUserId should be a Number but was: " + resolvedTargetUserId.getClass().getSimpleName());
        assertEquals(77, ((Number) resolvedTargetUserId).intValue());
    }

    // ──────────────────────────────────────────────────────────
    // Scenario 6: Create task step executes and emits outputs
    // ──────────────────────────────────────────────────────────
    @Test
    void shouldExecuteFubCreateTaskAndEmitOutputs() {
        seedTaskWorkflow();
        stubClient.setCreatedTaskResult(new CreatedTask(
                501L,
                7890L,
                77L,
                "Call back lead",
                LocalDate.of(2026, 1, 2),
                OffsetDateTime.parse("2026-01-02T09:30:00Z")));

        WorkflowPlanningResult plan = planTaskRun("7890", "evt-task-1");
        worker.pollAndProcessDueSteps();

        WorkflowRunEntity run = runRepository.findById(plan.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals("TASK_CREATED", run.getReasonCode());

        WorkflowRunStepEntity createTaskStep = findStepByNodeId(stepRepository.findByRunId(plan.runId()), "create_task");
        assertEquals(WorkflowRunStepStatus.COMPLETED, createTaskStep.getStatus());
        assertEquals("SUCCESS", createTaskStep.getResultCode());
        assertEquals(501L, ((Number) createTaskStep.getOutputs().get("taskId")).longValue());
        assertEquals(7890L, ((Number) createTaskStep.getOutputs().get("personId")).longValue());
        assertEquals(77L, ((Number) createTaskStep.getOutputs().get("assignedUserId")).longValue());
        assertEquals("Call back lead", createTaskStep.getOutputs().get("name"));

        assertEquals(1, stubClient.createTaskCommands.size());
        CreateTaskCommand issuedCommand = stubClient.createTaskCommands.getFirst();
        assertEquals(7890L, issuedCommand.personId());
        assertEquals(77L, issuedCommand.assignedUserId());
        assertEquals(LocalDate.of(2026, 1, 2), issuedCommand.dueDate());
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private void seedParityWorkflow() {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey("ASSIGNMENT_FOLLOWUP_SLA");
        entity.setName("Assignment Follow-Up SLA (Parity Test)");
        entity.setGraph(parityGraph());
        entity.setStatus(WorkflowStatus.ACTIVE);
        workflowRepository.saveAndFlush(entity);
    }

    private void seedTaskWorkflow() {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey("TASK_CREATE_PARITY");
        entity.setName("Task Create Workflow (Parity Test)");
        entity.setGraph(taskGraph());
        entity.setStatus(WorkflowStatus.ACTIVE);
        workflowRepository.saveAndFlush(entity);
    }

    private WorkflowPlanningResult planParityRun(String sourceLeadId, String eventId) {
        Map<String, Object> triggerPayload = new HashMap<>();
        triggerPayload.put("fallbackUserId", 77);
        triggerPayload.put("eventType", "peopleCreated");
        triggerPayload.put("resourceIds", List.of(Integer.parseInt(sourceLeadId)));

        return executionManager.plan(new WorkflowPlanRequest(
                "ASSIGNMENT_FOLLOWUP_SLA", "TEST", eventId, null, sourceLeadId, triggerPayload));
    }

    private WorkflowPlanningResult planTaskRun(String sourceLeadId, String eventId) {
        Map<String, Object> triggerPayload = new HashMap<>();
        triggerPayload.put("fallbackUserId", 77);
        triggerPayload.put("taskName", "Call back lead");
        triggerPayload.put("eventType", "peopleCreated");
        triggerPayload.put("resourceIds", List.of(Integer.parseInt(sourceLeadId)));

        return executionManager.plan(new WorkflowPlanRequest(
                "TASK_CREATE_PARITY", "TEST", eventId, null, sourceLeadId, triggerPayload));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parityGraph() {
        // Mirrors ASSIGNMENT_FOLLOWUP_SLA_V1:
        //   wait_claim → CLAIMED: terminal COMPLIANT_CLOSED
        //              → NOT_CLAIMED: [wait_comm]
        //   wait_comm  → CONVERSATIONAL: terminal COMPLIANT_CLOSED
        //              → CONNECTED_NON_CONVERSATIONAL: [do_reassign]
        //              → COMM_NOT_FOUND: [do_reassign]
        //   do_reassign → SUCCESS: terminal ACTION_COMPLETED
        //               → FAILED: terminal ACTION_FAILED
        Map<String, Object> waitClaim = new HashMap<>();
        waitClaim.put("id", "wait_claim");
        waitClaim.put("type", "wait_and_check_claim");
        waitClaim.put("config", Map.of("delayMinutes", 0));
        waitClaim.put("transitions", Map.of(
                "CLAIMED", Map.of("terminal", "COMPLIANT_CLOSED"),
                "NOT_CLAIMED", List.of("wait_comm")));

        Map<String, Object> waitComm = new HashMap<>();
        waitComm.put("id", "wait_comm");
        waitComm.put("type", "wait_and_check_communication");
        waitComm.put("config", Map.of("delayMinutes", 0));
        waitComm.put("transitions", Map.of(
                "CONVERSATIONAL", Map.of("terminal", "COMPLIANT_CLOSED"),
                "CONNECTED_NON_CONVERSATIONAL", List.of("do_reassign"),
                "COMM_NOT_FOUND", List.of("do_reassign")));

        Map<String, Object> doReassign = new HashMap<>();
        doReassign.put("id", "do_reassign");
        doReassign.put("type", "fub_reassign");
        doReassign.put("config", Map.of("targetUserId", "{{ event.payload.fallbackUserId }}"));
        doReassign.put("transitions", Map.of(
                "SUCCESS", Map.of("terminal", "ACTION_COMPLETED"),
                "FAILED", Map.of("terminal", "ACTION_FAILED")));

        Map<String, Object> graph = new HashMap<>();
        graph.put("schemaVersion", 1);
        graph.put("entryNode", "wait_claim");
        graph.put("nodes", List.of(waitClaim, waitComm, doReassign));
        return graph;
    }

    private Map<String, Object> taskGraph() {
        Map<String, Object> createTask = new HashMap<>();
        createTask.put("id", "create_task");
        createTask.put("type", "fub_create_task");
        createTask.put("config", Map.of(
                "name", "{{ event.payload.taskName }}",
                "assignedUserId", "{{ event.payload.fallbackUserId }}",
                "dueDate", "2026-01-02"));
        createTask.put("transitions", Map.of(
                "SUCCESS", Map.of("terminal", "TASK_CREATED"),
                "FAILED", Map.of("terminal", "TASK_FAILED")));

        Map<String, Object> graph = new HashMap<>();
        graph.put("schemaVersion", 1);
        graph.put("entryNode", "create_task");
        graph.put("nodes", List.of(createTask));
        return graph;
    }

    private WorkflowRunStepEntity findStepByNodeId(List<WorkflowRunStepEntity> steps, String nodeId) {
        return steps.stream()
                .filter(s -> nodeId.equals(s.getNodeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Step not found: " + nodeId));
    }

    private void saveOutboundCallEvidence(
            Long callId,
            String sourceLeadId,
            Integer durationSeconds,
            String outcome,
            int minutesAgo) {
        saveLocalCallEvidence(callId, sourceLeadId, false, durationSeconds, outcome, minutesAgo);
    }

    private void saveLocalCallEvidence(
            Long callId,
            String sourceLeadId,
            boolean isIncoming,
            Integer durationSeconds,
            String outcome,
            int minutesAgo) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRetryCount(0);
        entity.setSourceLeadId(sourceLeadId);
        entity.setIsIncoming(isIncoming);
        entity.setDurationSeconds(durationSeconds);
        entity.setOutcome(outcome);
        entity.setCallStartedAt(now.minusMinutes(minutesAgo));
        entity.setCreatedAt(now.minusMinutes(minutesAgo));
        entity.setUpdatedAt(now.minusMinutes(minutesAgo));
        processedCallRepository.saveAndFlush(entity);
    }

    // ──────────────────────────────────────────────────────────
    // Stub FUB client
    // ──────────────────────────────────────────────────────────

    static class StubFollowUpBossClient implements FollowUpBossClient {
        private final Map<Long, PersonDetails> personDetailsMap = new HashMap<>();
        private final Map<Long, PersonCommunicationCheckResult> commCheckMap = new HashMap<>();
        private volatile ActionExecutionResult reassignResult = ActionExecutionResult.ok();
        private volatile ActionExecutionResult moveToPondResult = ActionExecutionResult.ok();
        final CopyOnWriteArrayList<long[]> reassignCalls = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<long[]> moveToPondCalls = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<Long> commCheckCalls = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<CreateTaskCommand> createTaskCommands = new CopyOnWriteArrayList<>();
        private volatile CreatedTask createdTaskResult = new CreatedTask(
                100L,
                0L,
                null,
                "stub",
                null,
                null);

        void reset() {
            personDetailsMap.clear();
            commCheckMap.clear();
            reassignResult = ActionExecutionResult.ok();
            moveToPondResult = ActionExecutionResult.ok();
            reassignCalls.clear();
            moveToPondCalls.clear();
            commCheckCalls.clear();
            createTaskCommands.clear();
            createdTaskResult = new CreatedTask(100L, 0L, null, "stub", null, null);
        }

        void setPersonDetails(Long personId, PersonDetails details) {
            personDetailsMap.put(personId, details);
        }

        void setCommunicationCheckResult(Long personId, PersonCommunicationCheckResult result) {
            commCheckMap.put(personId, result);
        }

        void setReassignResult(ActionExecutionResult result) {
            this.reassignResult = result;
        }

        void setMoveToPondResult(ActionExecutionResult result) {
            this.moveToPondResult = result;
        }

        void setCreatedTaskResult(CreatedTask result) {
            this.createdTaskResult = result;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return null;
        }

        @Override
        public CallDetails getCallById(long callId) {
            return null;
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            return personDetailsMap.get(personId);
        }

        @Override
        public JsonNode getPersonRawById(long personId) {
            throw new UnsupportedOperationException("Not used in workflow parity tests");
        }

        @Override
        public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
            commCheckCalls.add(personId);
            return commCheckMap.get(personId);
        }

        @Override
        public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
            reassignCalls.add(new long[]{personId, targetUserId});
            return reassignResult;
        }

        @Override
        public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
            moveToPondCalls.add(new long[]{personId, targetPondId});
            return moveToPondResult;
        }

        @Override
        public ActionExecutionResult addTag(long personId, String tagName) {
            return ActionExecutionResult.ok();
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            createTaskCommands.add(command);
            return createdTaskResult;
        }
    }
}
