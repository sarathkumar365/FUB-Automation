package com.fuba.automation_engine.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
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
import com.fuba.automation_engine.service.webhook.WebhookEventProcessorService;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.workflow.WorkflowStepExecutionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WorkflowAdminApiIntegrationTest {

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
            return new MutableTestClock(Instant.parse("2026-02-01T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        FollowUpBossClient followUpBossClient() {
            return new StubFollowUpBossClient();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookEventProcessorService webhookEventProcessorService;

    @Autowired
    private WorkflowRunStepClaimRepository workflowRunStepClaimRepository;

    @Autowired
    private WorkflowStepExecutionService workflowStepExecutionService;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WorkflowRunStepRepository workflowRunStepRepository;

    @Autowired
    private AutomationWorkflowRepository automationWorkflowRepository;

    @Autowired
    private MutableTestClock testClock;

    @Autowired
    private FollowUpBossClient followUpBossClient;

    @BeforeEach
    void setUp() {
        workflowRunStepRepository.deleteAll();
        workflowRunRepository.deleteAll();
        automationWorkflowRepository.deleteAll();
        testClock.set(Instant.parse("2026-02-01T10:00:00Z"));

        if (followUpBossClient instanceof StubFollowUpBossClient stubClient) {
            stubClient.reset();
        }
    }

    @Test
    void adminWorkflowApi_endToEnd_closingFixes() throws Exception {
        String workflowKey = "e2e-wf";

        mockMvc.perform(get("/admin/workflows/step-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));

        mockMvc.perform(get("/admin/workflows/trigger-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("webhook_fub")));

        String validValidationRequest = """
                {
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validValidationRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        String invalidValidationRequest = """
                {
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "n1",
                    "nodes": [
                      {
                        "id": "n1",
                        "type": "unknown_step",
                        "config": {},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED"
                    }
                  }
                }
                """;
        mockMvc.perform(post("/admin/workflows/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidValidationRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors", hasItem("Node 'n1' references unknown step type: unknown_step")));

        String createRequest = """
                {
                  "key": "e2e-wf",
                  "name": "E2E WF v1",
                  "description": "Wave 4a integration",
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED",
                      "filter": "event.payload.channel = \\"zillow\\""
                    }
                  },
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "status": "INACTIVE"
                }
                """;
        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        webhookEventProcessorService.process(webhook("evt-wave4a-1", "zillow", 777L));
        WorkflowRunEntity v1Run = latestRunForKey(workflowKey);
        JsonNode v1Snapshot = objectMapper.valueToTree(v1Run.getWorkflowGraphSnapshot());

        String updateRequest = """
                {
                  "name": "E2E WF v2",
                  "description": "Wave 4a integration v2",
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d2",
                    "nodes": [
                      {
                        "id": "d2",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  }
                }
                """;
        mockMvc.perform(put("/admin/workflows/" + workflowKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        executeUntilRunTerminal(v1Run.getId(), Duration.ofSeconds(5));
        WorkflowRunEntity v1Terminal = workflowRunRepository.findById(v1Run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, v1Terminal.getStatus());
        assertEquals(1L, v1Terminal.getWorkflowVersion());
        assertEquals(v1Snapshot, objectMapper.valueToTree(v1Terminal.getWorkflowGraphSnapshot()));

        mockMvc.perform(get("/admin/workflows/" + workflowKey + "/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(v1Run.getId()))
                .andExpect(jsonPath("$.items[0].workflowVersionNumber").value(1));

        mockMvc.perform(get("/admin/workflow-runs/" + v1Run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v1Run.getId()))
                .andExpect(jsonPath("$.workflowVersionNumber").value(1))
                .andExpect(jsonPath("$.steps.length()").value(greaterThan(0)));

        webhookEventProcessorService.process(webhook("evt-wave4a-2", "zillow", 778L));
        WorkflowRunEntity v2Run = latestRunForKey(workflowKey);
        executeUntilRunTerminal(v2Run.getId(), Duration.ofSeconds(5));
        WorkflowRunEntity v2Terminal = workflowRunRepository.findById(v2Run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, v2Terminal.getStatus());
        assertEquals(2L, v2Terminal.getWorkflowVersion());

        mockMvc.perform(get("/admin/workflows/" + workflowKey + "/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(v2Run.getId()))
                .andExpect(jsonPath("$.items[0].workflowVersionNumber").value(2));

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        AutomationWorkflowEntity version1 = automationWorkflowRepository.findByKeyAndVersionNumber(workflowKey, 1).orElseThrow();
        AutomationWorkflowEntity version3 = automationWorkflowRepository.findByKeyAndVersionNumber(workflowKey, 3).orElseThrow();
        assertEquals(version1.getGraph(), version3.getGraph());

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        webhookEventProcessorService.process(webhook("evt-wave4a-3", "zillow", 779L));
        WorkflowRunEntity v3Run = latestRunForKey(workflowKey);
        executeUntilRunTerminal(v3Run.getId(), Duration.ofSeconds(5));
        WorkflowRunEntity v3Terminal = workflowRunRepository.findById(v3Run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, v3Terminal.getStatus());
        assertEquals(3L, v3Terminal.getWorkflowVersion());

        mockMvc.perform(get("/admin/workflows/" + workflowKey + "/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(v3Run.getId()))
                .andExpect(jsonPath("$.items[0].workflowVersionNumber").value(3));

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        long runCountBefore = workflowRunRepository.count();
        webhookEventProcessorService.process(webhook("evt-wave4a-4", "zillow", 780L));
        assertEquals(runCountBefore, workflowRunRepository.count());

        mockMvc.perform(delete("/admin/workflows/" + workflowKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/admin/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cancelRunShouldStopFurtherWorkerPickup() throws Exception {
        String workflowKey = "cancel-wf";

        String createRequest = """
                {
                  "key": "cancel-wf",
                  "name": "Cancel WF",
                  "description": "Wave 4c cancel smoke",
                  "trigger": {
                    "type": "webhook_fub",
                    "config": {
                      "eventDomain": "ASSIGNMENT",
                      "eventAction": "UPDATED",
                      "filter": "event.payload.channel = \\"zillow\\""
                    }
                  },
                  "graph": {
                    "schemaVersion": 1,
                    "entryNode": "d1",
                    "nodes": [
                      {
                        "id": "d1",
                        "type": "delay",
                        "config": {"delayMinutes": 0},
                        "transitions": {"DONE": {"terminal": "COMPLETED"}}
                      }
                    ]
                  },
                  "status": "INACTIVE"
                }
                """;

        mockMvc.perform(post("/admin/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/admin/workflows/" + workflowKey + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        webhookEventProcessorService.process(webhook("evt-wave4c-cancel", "zillow", 880L));
        WorkflowRunEntity run = latestRunForKey(workflowKey);

        mockMvc.perform(post("/admin/workflow-runs/" + run.getId() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.reasonCode").value("CANCELED_BY_OPERATOR"));

        List<WorkflowRunStepClaimRepository.ClaimedStepRow> claimed = workflowRunStepClaimRepository
                .claimDuePendingSteps(OffsetDateTime.now(testClock), 25);
        assertEquals(0, claimed.size());
    }

    private WorkflowRunEntity latestRunForKey(String workflowKey) {
        return workflowRunRepository.findAll().stream()
                .filter(run -> workflowKey.equals(run.getWorkflowKey()))
                .max((left, right) -> Long.compare(left.getId(), right.getId()))
                .orElseThrow(() -> new AssertionError("No workflow run found for key=" + workflowKey));
    }

    private void executeUntilRunTerminal(Long runId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            WorkflowRunEntity run = workflowRunRepository.findById(runId).orElseThrow();
            if (run.getStatus() == WorkflowRunStatus.COMPLETED || run.getStatus() == WorkflowRunStatus.FAILED) {
                return;
            }

            List<WorkflowRunStepClaimRepository.ClaimedStepRow> claimed = workflowRunStepClaimRepository
                    .claimDuePendingSteps(OffsetDateTime.now(testClock), 25);

            if (claimed.isEmpty()) {
                testClock.advance(Duration.ofMillis(50));
                continue;
            }

            for (WorkflowRunStepClaimRepository.ClaimedStepRow row : claimed) {
                workflowStepExecutionService.executeClaimedStep(row);
            }
        }

        WorkflowRunEntity run = workflowRunRepository.findById(runId).orElseThrow();
        throw new AssertionError("Run did not reach terminal state within timeout; status=" + run.getStatus());
    }

    private Map<String, Object> triggerConfig(String eventDomain, String eventAction, String filter) {
        return Map.of(
                "eventDomain", eventDomain,
                "eventAction", eventAction,
                "filter", filter);
    }

    private NormalizedWebhookEvent webhook(String eventId, String channel, long leadId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", "peopleUpdated");
        payload.put("channel", channel);
        payload.put("fallbackUserId", 77);
        payload.putArray("resourceIds").add(leadId);

        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                eventId,
                "peopleUpdated",
                null,
                null,
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(testClock),
                "hash-" + eventId);
    }

    static final class MutableTestClock extends Clock {

        private final ZoneId zone;
        private final AtomicReference<Instant> instant;

        MutableTestClock(Instant initial, ZoneId zone) {
            this.zone = zone;
            this.instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zone;
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

    static final class StubFollowUpBossClient implements FollowUpBossClient {

        private final CopyOnWriteArrayList<String> addTagCalls = new CopyOnWriteArrayList<>();

        void reset() {
            addTagCalls.clear();
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(
                    0L,
                    command == null ? null : command.event(),
                    command == null ? null : command.url(),
                    "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            throw new UnsupportedOperationException("Not used in workflow admin integration test");
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            throw new UnsupportedOperationException("Not used in workflow admin integration test");
        }

        @Override
        public JsonNode getPersonRawById(long personId) {
            throw new UnsupportedOperationException("Not used in workflow admin integration test");
        }

        @Override
        public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
            throw new UnsupportedOperationException("Not used in workflow admin integration test");
        }

        @Override
        public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
            return ActionExecutionResult.ok();
        }

        @Override
        public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
            return ActionExecutionResult.ok();
        }

        @Override
        public ActionExecutionResult addTag(long personId, String tagName) {
            addTagCalls.add(personId + ":" + tagName);
            return ActionExecutionResult.ok();
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            throw new UnsupportedOperationException("Not used in workflow admin integration test");
        }
    }
}
