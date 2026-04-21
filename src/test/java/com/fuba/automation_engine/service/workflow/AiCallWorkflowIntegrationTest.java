package com.fuba.automation_engine.service.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fuba.automation_engine.config.WorkflowWorkerProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClient;
import com.fuba.automation_engine.service.workflow.aicall.AiCallServiceClientException;
import com.fuba.automation_engine.service.workflow.aicall.GetCallResponse;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallRequest;
import com.fuba.automation_engine.service.workflow.aicall.PlaceCallResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class AiCallWorkflowIntegrationTest {

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
        MutableTestClock testClock() {
            return new MutableTestClock(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        StubAiCallServiceClient stubAiCallServiceClient() {
            return new StubAiCallServiceClient();
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
    private MutableTestClock testClock;

    @Autowired
    private StubAiCallServiceClient stubAiCallServiceClient;

    @PersistenceContext
    private EntityManager entityManager;

    private WorkflowExecutionDueWorker worker;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        workflowRepository.deleteAll();
        testClock.set(Instant.parse("2026-03-01T10:00:00Z"));
        stubAiCallServiceClient.reset();
        worker = new WorkflowExecutionDueWorker(workerProperties, stepClaimRepository, stepExecutionService, testClock);
    }

    @Test
    void shouldRescheduleSameStepThenCompleteAndExposeTerminalOutputToDownstreamStep() {
        seedActiveWorkflow("AI_CALL_WF_A", aiCallGraph());
        stubAiCallServiceClient.enqueueGet(new GetCallResponse("CA_1", "in_progress", null));
        Map<String, Object> completedPayload = new LinkedHashMap<>();
        completedPayload.put("schema_version", "1");
        completedPayload.put("call_sid", "CA_1");
        completedPayload.put("status", "completed");
        completedPayload.put("conversation", Map.of("interested", "yes"));
        completedPayload.put("error", null);
        stubAiCallServiceClient.enqueueGet(new GetCallResponse("CA_1", "completed", completedPayload));

        WorkflowPlanningResult plan = executionManager.plan(
                new WorkflowPlanRequest("AI_CALL_WF_A", "TEST", "evt-ai-a", null, "lead-1", null));
        Long runId = plan.runId();
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, plan.status());

        // Poll #1: place call -> reschedule
        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity aiStep = findStep(runId, "ai1");
        assertEquals(WorkflowRunStepStatus.PENDING, aiStep.getStatus());
        assertEquals(OffsetDateTime.parse("2026-03-01T10:02:00Z"), aiStep.getDueAt());
        assertNotNull(aiStep.getStepState());
        assertEquals("CA_1", aiStep.getStepState().get("callSid"));
        assertEquals(runId + ":" + aiStep.getId(), aiStep.getStepState().get("callKey"));
        assertEquals(1, stubAiCallServiceClient.placeRequests().size());
        assertEquals(runId + ":" + aiStep.getId(), stubAiCallServiceClient.placeRequests().getFirst().callKey());

        // Poll #2: in_progress -> reschedule
        testClock.advance(Duration.ofSeconds(120));
        worker.pollAndProcessDueSteps();
        aiStep = findStep(runId, "ai1");
        assertEquals(WorkflowRunStepStatus.PENDING, aiStep.getStatus());
        assertEquals(OffsetDateTime.parse("2026-03-01T10:04:00Z"), aiStep.getDueAt());

        // Poll #3: completed -> transitions to set_variable and terminal
        testClock.advance(Duration.ofSeconds(120));
        worker.pollAndProcessDueSteps();

        aiStep = findStep(runId, "ai1");
        WorkflowRunStepEntity setStep = findStep(runId, "sv1");
        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();

        assertEquals(WorkflowRunStepStatus.COMPLETED, aiStep.getStatus());
        assertEquals("completed", aiStep.getResultCode());
        assertEquals("completed", aiStep.getOutputs().get("status"));
        assertEquals("1", aiStep.getOutputs().get("schema_version"));

        assertEquals(WorkflowRunStepStatus.COMPLETED, setStep.getStatus());
        assertEquals("DONE", setStep.getResultCode());
        assertEquals("completed", setStep.getOutputs().get("callStatus"));

        WorkflowRunStepEntity setStepSid = findStep(runId, "sv2");
        assertEquals(WorkflowRunStepStatus.COMPLETED, setStepSid.getStatus());
        assertEquals("CA_1", setStepSid.getOutputs().get("callSid"));

        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
        assertEquals("COMPLETED", run.getReasonCode());
    }

    @Test
    void shouldEmitTimeoutPayloadAndExposeTimeoutStatusDownstream() {
        seedActiveWorkflow("AI_CALL_WF_B", aiCallGraph());
        stubAiCallServiceClient.enqueueGet(new GetCallResponse("CA_1", "in_progress", null));

        WorkflowPlanningResult plan = executionManager.plan(
                new WorkflowPlanRequest("AI_CALL_WF_B", "TEST", "evt-ai-b", null, "lead-2", null));
        Long runId = plan.runId();

        // Poll #1: place call -> reschedule
        worker.pollAndProcessDueSteps();
        WorkflowRunStepEntity aiStep = findStep(runId, "ai1");
        assertEquals(OffsetDateTime.parse("2026-03-01T10:02:00Z"), aiStep.getDueAt());

        // Poll #2: after >5m age, in_progress -> timeout terminal payload
        testClock.advance(Duration.ofSeconds(301));
        worker.pollAndProcessDueSteps();

        aiStep = findStep(runId, "ai1");
        WorkflowRunStepEntity setStep = findStep(runId, "sv1");
        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();

        assertEquals(WorkflowRunStepStatus.COMPLETED, aiStep.getStatus());
        assertEquals("timeout", aiStep.getResultCode());
        assertEquals("timeout", aiStep.getOutputs().get("status"));
        assertEquals("1", aiStep.getOutputs().get("schema_version"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) aiStep.getOutputs().get("error");
        assertEquals("call_timeout", error.get("code"));

        assertEquals(WorkflowRunStepStatus.COMPLETED, setStep.getStatus());
        assertEquals("timeout", setStep.getOutputs().get("callStatus"));
        assertEquals(WorkflowRunStatus.COMPLETED, run.getStatus());
    }

    @Test
    void shouldFailRunWhenPollingReturnsNonTransientClientError() {
        seedActiveWorkflow("AI_CALL_WF_C", aiCallGraph());
        stubAiCallServiceClient.enqueueGetException(new AiCallServiceClientException("permanent", false, 400));

        WorkflowPlanningResult plan = executionManager.plan(
                new WorkflowPlanRequest("AI_CALL_WF_C", "TEST", "evt-ai-c", null, "lead-3", null));
        Long runId = plan.runId();

        // Poll #1: place call -> reschedule
        worker.pollAndProcessDueSteps();

        // Poll #2: non-transient poll error -> failed run
        testClock.advance(Duration.ofSeconds(120));
        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity aiStep = findStep(runId, "ai1");
        WorkflowRunStepEntity setStep = findStep(runId, "sv1");
        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();

        assertEquals(WorkflowRunStepStatus.FAILED, aiStep.getStatus());
        assertNull(aiStep.getResultCode());
        assertTrue(aiStep.getErrorMessage().contains("GET /calls/{sid} failed"));
        assertEquals(WorkflowRunStatus.FAILED, run.getStatus());
        assertEquals("AI_CALL_POLL_FAILED", run.getReasonCode());
        assertEquals(WorkflowRunStepStatus.WAITING_DEPENDENCY, setStep.getStatus());
    }

    @Test
    void shouldFailRunTerminallyOnFirstTransientPlaceCallFailure() {
        seedActiveWorkflow("AI_CALL_WF_E", aiCallGraph());
        stubAiCallServiceClient.enqueuePlaceException(new AiCallServiceClientException("blip", true, 503));

        WorkflowPlanningResult plan = executionManager.plan(
                new WorkflowPlanRequest("AI_CALL_WF_E", "TEST", "evt-ai-e", null, "lead-5", null));
        Long runId = plan.runId();

        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity aiStep = findStep(runId, "ai1");
        WorkflowRunStepEntity setStep = findStep(runId, "sv1");
        WorkflowRunEntity run = runRepository.findById(runId).orElseThrow();

        // Phase 3 spec: NO_RETRY default → a single transient POST /call failure terminates
        // the run immediately with reasonCode AI_CALL_PLACE_FAILED.
        assertEquals(1, stubAiCallServiceClient.placeRequests().size());
        assertEquals(WorkflowRunStepStatus.FAILED, aiStep.getStatus());
        assertNull(aiStep.getResultCode());
        assertTrue(aiStep.getErrorMessage().contains("POST /call failed"));
        assertEquals(WorkflowRunStatus.FAILED, run.getStatus());
        assertEquals("AI_CALL_PLACE_FAILED", run.getReasonCode());
        assertEquals(WorkflowRunStepStatus.WAITING_DEPENDENCY, setStep.getStatus());
    }

    @Test
    void shouldPersistStepStateAcrossWorkerEntityManagerBoundary() {
        seedActiveWorkflow("AI_CALL_WF_D", aiCallGraph());
        Map<String, Object> completedPayload = new LinkedHashMap<>();
        completedPayload.put("schema_version", "1");
        completedPayload.put("call_sid", "CA_1");
        completedPayload.put("status", "completed");
        completedPayload.put("conversation", Map.of("interested", "yes"));
        stubAiCallServiceClient.enqueueGet(new GetCallResponse("CA_1", "completed", completedPayload));

        WorkflowPlanningResult plan = executionManager.plan(
                new WorkflowPlanRequest("AI_CALL_WF_D", "TEST", "evt-ai-d", null, "lead-4", null));
        Long runId = plan.runId();

        // Poll #1: place call -> reschedule, step_state persisted.
        worker.pollAndProcessDueSteps();

        // Evict persistence context to prove the next poll reads state from the database
        // rather than a cached entity.
        entityManager.clear();

        assertTrue(stubAiCallServiceClient.pollSids().isEmpty());

        // Poll #2: after advancing past the reschedule delay, state must be re-hydrated from DB
        // and the callSid must drive the GET.
        testClock.advance(Duration.ofSeconds(120));
        worker.pollAndProcessDueSteps();

        WorkflowRunStepEntity aiStep = findStep(runId, "ai1");
        assertEquals(WorkflowRunStepStatus.COMPLETED, aiStep.getStatus());
        assertEquals("completed", aiStep.getResultCode());
        assertEquals(List.of("CA_1"), stubAiCallServiceClient.pollSids());
    }

    private void seedActiveWorkflow(String key, Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("AI call workflow " + key);
        entity.setGraph(graph);
        entity.setStatus(WorkflowStatus.ACTIVE);
        workflowRepository.saveAndFlush(entity);
    }

    private WorkflowRunStepEntity findStep(Long runId, String nodeId) {
        return stepRepository.findByRunId(runId).stream()
                .filter(step -> nodeId.equals(step.getNodeId()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> aiCallGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "ai1",
                "nodes", List.of(
                        Map.of(
                                "id", "ai1",
                                "type", "ai_call",
                                "config", Map.of(
                                        "to", "+15555550111",
                                        "context", Map.of("lead_name", "Sarah")),
                                "transitions", Map.of(
                                        "completed", List.of("sv1"),
                                        "timeout", List.of("sv1"),
                                        "failed", Map.of("terminal", "CALL_FAILED"))),
                        Map.of(
                                "id", "sv1",
                                "type", "set_variable",
                                "config", Map.of(
                                        "variableName", "callStatus",
                                        "value", "{{ steps.ai1.outputs.status }}"),
                                "transitions", Map.of("DONE", List.of("sv2"))),
                        Map.of(
                                "id", "sv2",
                                "type", "set_variable",
                                "config", Map.of(
                                        "variableName", "callSid",
                                        "value", "{{ steps.ai1.outputs.call_sid }}"),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    static final class MutableTestClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zoneId;

        MutableTestClock(Instant initial, ZoneId zoneId) {
            this.instant = new AtomicReference<>(initial);
            this.zoneId = zoneId;
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableTestClock(instant.get(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    static final class StubAiCallServiceClient implements AiCallServiceClient {

        private final AtomicInteger callSidCounter = new AtomicInteger(1);
        private final CopyOnWriteArrayList<PlaceCallRequest> placeRequests = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<String> pollSids = new CopyOnWriteArrayList<>();
        private final Deque<Object> getScript = new ArrayDeque<>();
        private final Deque<RuntimeException> placeExceptions = new ArrayDeque<>();

        @Override
        public synchronized PlaceCallResponse placeCall(PlaceCallRequest request) {
            placeRequests.add(request);
            RuntimeException scripted = placeExceptions.pollFirst();
            if (scripted != null) {
                throw scripted;
            }
            return new PlaceCallResponse("CA_" + callSidCounter.getAndIncrement(), "in_progress");
        }

        synchronized void enqueuePlaceException(RuntimeException ex) {
            placeExceptions.addLast(ex);
        }

        @Override
        public synchronized GetCallResponse getCall(String callSid) {
            pollSids.add(callSid);
            Object next = getScript.pollFirst();
            if (next == null) {
                return new GetCallResponse(callSid, "in_progress", null);
            }
            if (next instanceof RuntimeException ex) {
                throw ex;
            }
            return (GetCallResponse) next;
        }

        synchronized void enqueueGet(GetCallResponse response) {
            getScript.addLast(response);
        }

        synchronized void enqueueGetException(RuntimeException ex) {
            getScript.addLast(ex);
        }

        synchronized void reset() {
            callSidCounter.set(1);
            placeRequests.clear();
            pollSids.clear();
            getScript.clear();
            placeExceptions.clear();
        }

        List<PlaceCallRequest> placeRequests() {
            return placeRequests;
        }

        List<String> pollSids() {
            return pollSids;
        }
    }
}
