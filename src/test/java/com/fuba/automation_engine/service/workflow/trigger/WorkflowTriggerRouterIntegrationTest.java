package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.event.DomainEvent;
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
