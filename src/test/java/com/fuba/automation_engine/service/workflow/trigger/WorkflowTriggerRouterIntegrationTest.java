package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        registry.add("policy.worker.enabled", () -> "false");
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
    void shouldCreateRunsForMatchingWorkflow() {
        seedWorkflow("WF_ASSIGNMENT", WorkflowStatus.ACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", null));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payload("peopleUpdated", "zillow", 777, 778)));

        assertEquals(1, summary.activeWorkflowCount());
        assertEquals(1, summary.matchedWorkflowCount());
        assertEquals(2, summary.plannedCount());
        assertEquals(2, runRepository.count());

        Set<String> leadIds = runRepository.findAll().stream()
                .map(WorkflowRunEntity::getSourceLeadId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("777", "778"), leadIds);
    }

    @Test
    void shouldNotCreateRunWhenFilterDoesNotMatch() {
        seedWorkflow("WF_FILTERED", WorkflowStatus.ACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", "event.payload.channel = \"zillow\""));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payload("peopleUpdated", "manual", 777)));

        assertEquals(1, summary.activeWorkflowCount());
        assertEquals(0, summary.matchedWorkflowCount());
        assertEquals(0, summary.plannedCount());
        assertEquals(0, runRepository.count());
    }

    @Test
    void shouldPlanAcrossMultipleWorkflowsAndEntities() {
        seedWorkflow("WF_A", WorkflowStatus.ACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", null));
        seedWorkflow("WF_B", WorkflowStatus.ACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", null));

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payload("peopleUpdated", "zillow", 900, 901)));

        assertEquals(2, summary.activeWorkflowCount());
        assertEquals(2, summary.matchedWorkflowCount());
        assertEquals(4, summary.plannedCount());
        assertEquals(4, runRepository.count());
    }

    @Test
    void shouldSkipInactiveUnknownAndNullTriggers() {
        seedWorkflow("WF_ACTIVE", WorkflowStatus.ACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", null));
        seedWorkflow("WF_INACTIVE", WorkflowStatus.INACTIVE, triggerConfig("ASSIGNMENT", "UPDATED", null));
        seedWorkflowWithRawTrigger("WF_UNKNOWN", WorkflowStatus.ACTIVE, Map.of("type", "unknown", "config", Map.of()));
        seedWorkflowWithRawTrigger("WF_NULL_TRIGGER", WorkflowStatus.ACTIVE, null);

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payload("peopleUpdated", "zillow", 123)));

        assertEquals(3, summary.activeWorkflowCount());
        assertEquals(1, summary.matchedWorkflowCount());
        assertEquals(1, summary.plannedCount());
        assertTrue(summary.skippedCount() >= 2);
        assertEquals(1, runRepository.count());
    }

    private void seedWorkflow(String key, WorkflowStatus status, Map<String, Object> triggerConfig) {
        seedWorkflowWithRawTrigger(key, status, Map.of("type", FubWebhookTriggerType.TRIGGER_TYPE_ID, "config", triggerConfig));
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

    private Map<String, Object> triggerConfig(String domain, String action, String filter) {
        if (filter == null) {
            return Map.of(
                    "eventDomain", domain,
                    "eventAction", action);
        }
        return Map.of(
                "eventDomain", domain,
                "eventAction", action,
                "filter", filter);
    }

    private NormalizedWebhookEvent event(ObjectNode payload) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-router-integration",
                payload.path("eventType").asText(""),
                null,
                null,
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash-router-integration");
    }

    private ObjectNode payload(String eventType, String channel, long... resourceIds) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", eventType);
        payload.put("channel", channel);
        var ids = payload.putArray("resourceIds");
        for (long resourceId : resourceIds) {
            ids.add(resourceId);
        }
        return payload;
    }
}
