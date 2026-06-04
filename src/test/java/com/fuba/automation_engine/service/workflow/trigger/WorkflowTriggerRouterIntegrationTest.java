package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.event.DomainEvent;
import com.fuba.automation_engine.service.workflow.WorkflowExecutionManager;
import com.fuba.automation_engine.service.workflow.WorkflowPlanRequest;
import com.fuba.automation_engine.service.workflow.WorkflowPlanningResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunControlService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowTriggerRouterIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowTriggerRouter router;

    @Autowired
    private WorkflowExecutionManager workflowExecutionManager;

    @Autowired
    private AutomationWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowRunRepository runRepository;

    @Autowired
    private WorkflowRunStepRepository stepRepository;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    void shouldCreateRunForMatchingWorkflow() {
        seedWorkflow("WF_PERSON", WorkflowStatus.ACTIVE, trigger(null));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event("zillow", 777));

        assertEquals(1, summary.activeWorkflowCount());
        assertEquals(1, summary.matchedWorkflowCount());
        assertEquals(1, summary.plannedCount());
        assertEquals(1, runRepository.count());
        assertEquals("777", runRepository.findAll().getFirst().getSourcePersonId());
    }

    @Test
    void shouldNotCreateRunWhenFilterDoesNotMatch() {
        seedWorkflow("WF_FILTERED", WorkflowStatus.ACTIVE, trigger("event.payload.channel = \"zillow\""));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event("manual", 777));

        assertEquals(1, summary.activeWorkflowCount());
        assertEquals(0, summary.matchedWorkflowCount());
        assertEquals(0, summary.plannedCount());
        assertEquals(0, runRepository.count());
    }

    @Test
    void shouldPlanAcrossMultipleMatchingWorkflows() {
        seedWorkflow("WF_A", WorkflowStatus.ACTIVE, trigger(null));
        seedWorkflow("WF_B", WorkflowStatus.ACTIVE, trigger(null));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event("zillow", 900));

        assertEquals(2, summary.activeWorkflowCount());
        assertEquals(2, summary.matchedWorkflowCount());
        assertEquals(2, summary.plannedCount());
        assertEquals(2, runRepository.count());
    }

    @Test
    void shouldSkipInactiveWrongKindAndNullTriggers() {
        seedWorkflow("WF_ACTIVE", WorkflowStatus.ACTIVE, trigger(null));
        seedWorkflow("WF_INACTIVE", WorkflowStatus.INACTIVE, trigger(null));
        seedWorkflowWithRawTrigger("WF_OTHER_KIND", WorkflowStatus.ACTIVE, Map.of("on", "person.created"));
        seedWorkflowWithRawTrigger("WF_NULL_TRIGGER", WorkflowStatus.ACTIVE, null);

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event("zillow", 123));

        assertEquals(3, summary.activeWorkflowCount());
        assertEquals(1, summary.matchedWorkflowCount());
        assertEquals(1, summary.plannedCount());
        assertTrue(summary.skippedCount() >= 2);
        assertEquals(1, runRepository.count());
    }

    @Test
    void shouldSupersedeInFlightRunWhenNewerEventOverlapsChangedFields() {
        seedWorkflow("WF_SUP", WorkflowStatus.ACTIVE, trigger(null));

        WorkflowPlanningResult first = workflowExecutionManager.plan(
                planRequest("WF_SUP", "700", "evt-1", List.of("assignedUserId")));
        WorkflowPlanningResult second = workflowExecutionManager.plan(
                planRequest("WF_SUP", "700", "evt-2", List.of("assignedUserId")));

        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, first.status());
        assertEquals(WorkflowPlanningResult.PlanningStatus.PLANNED, second.status());

        WorkflowRunEntity firstRun = runRepository.findById(first.runId()).orElseThrow();
        assertEquals(WorkflowRunStatus.CANCELED, firstRun.getStatus());
        assertEquals(WorkflowRunControlService.SUPERSEDED_BY_NEWER_EVENT, firstRun.getReasonCode());
        assertEquals(WorkflowRunStatus.PENDING, runRepository.findById(second.runId()).orElseThrow().getStatus());
    }

    @Test
    void shouldNotSupersedeWhenNewerEventChangesDifferentFields() {
        seedWorkflow("WF_NOV", WorkflowStatus.ACTIVE, trigger(null));

        WorkflowPlanningResult first = workflowExecutionManager.plan(
                planRequest("WF_NOV", "701", "evt-a", List.of("assignedUserId")));
        WorkflowPlanningResult second = workflowExecutionManager.plan(
                planRequest("WF_NOV", "701", "evt-b", List.of("phone")));

        assertEquals(WorkflowRunStatus.PENDING, runRepository.findById(first.runId()).orElseThrow().getStatus());
        assertEquals(WorkflowRunStatus.PENDING, runRepository.findById(second.runId()).orElseThrow().getStatus());
    }

    @Test
    void shouldScopeSupersedePerWorkflowKey() {
        seedWorkflow("WF_X", WorkflowStatus.ACTIVE, trigger(null));
        seedWorkflow("WF_Y", WorkflowStatus.ACTIVE, trigger(null));

        WorkflowPlanningResult x1 = workflowExecutionManager.plan(
                planRequest("WF_X", "702", "evt-x1", List.of("assignedUserId")));
        WorkflowPlanningResult y1 = workflowExecutionManager.plan(
                planRequest("WF_Y", "702", "evt-y1", List.of("assignedUserId")));
        WorkflowPlanningResult x2 = workflowExecutionManager.plan(
                planRequest("WF_X", "702", "evt-x2", List.of("assignedUserId")));

        assertEquals(WorkflowRunStatus.CANCELED, runRepository.findById(x1.runId()).orElseThrow().getStatus());
        assertEquals(WorkflowRunStatus.PENDING, runRepository.findById(y1.runId()).orElseThrow().getStatus());
        assertEquals(WorkflowRunStatus.PENDING, runRepository.findById(x2.runId()).orElseThrow().getStatus());
    }

    private WorkflowPlanRequest planRequest(String workflowKey, String personId, String eventId, List<String> changedFields) {
        Map<String, Object> payload = Map.of("changed_fields", changedFields);
        return new WorkflowPlanRequest(workflowKey, "FUB", eventId, null, personId, payload, null);
    }

    private void seedWorkflow(String key, WorkflowStatus status, Map<String, Object> trigger) {
        seedWorkflowWithRawTrigger(key, status, trigger);
    }

    private void seedWorkflowWithRawTrigger(String key, WorkflowStatus status, Map<String, Object> trigger) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("Workflow " + key);
        entity.setStatus(status);
        entity.setTrigger(trigger);
        entity.setGraph(simpleDelayGraph());
        workflowRepository.saveAndFlush(entity);
    }

    private Map<String, Object> simpleDelayGraph() {
        return Map.of(
                "schemaVersion", 1,
                "entryNode", "d1",
                "nodes", List.of(
                        Map.of(
                                "id", "d1",
                                "type", "delay",
                                "config", Map.of("delayMinutes", 0),
                                "transitions", Map.of("DONE", Map.of("terminal", "COMPLETED")))));
    }

    private Map<String, Object> trigger(String filter) {
        if (filter == null) {
            return Map.of("on", "person.state_changed");
        }
        return Map.of("on", "person.state_changed", "filter", filter);
    }

    /** id null → run.domainEventId null (no events row to FK against); dedup falls back to key+source+person. */
    private DomainEvent event(String channel, long entityId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("channel", channel);
        payload.set("changed_fields", objectMapper.valueToTree(List.of("assignedUserId")));
        return new DomainEvent(null, "person.state_changed", "FUB", null, "person", String.valueOf(entityId), payload);
    }
}
