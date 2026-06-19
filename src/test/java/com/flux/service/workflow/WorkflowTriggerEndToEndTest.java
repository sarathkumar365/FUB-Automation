package com.flux.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flux.persistence.entity.AutomationWorkflowEntity;
import com.flux.persistence.entity.WorkflowRunEntity;
import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.entity.WorkflowRunStepEntity;
import com.flux.persistence.entity.WorkflowRunStepStatus;
import com.flux.persistence.entity.WorkflowStatus;
import com.flux.persistence.repository.AutomationWorkflowRepository;
import com.flux.persistence.repository.WorkflowRunRepository;
import com.flux.persistence.repository.WorkflowRunStepClaimRepository;
import com.flux.persistence.repository.WorkflowRunStepRepository;
import com.flux.service.FollowUpBossClient;
import com.flux.service.model.ActionExecutionResult;
import com.flux.service.model.CallDetails;
import com.flux.service.model.CreateNoteCommand;
import com.flux.service.model.CreatedNote;
import com.flux.service.model.CreateTaskCommand;
import com.flux.service.model.CreatedTask;
import com.flux.service.model.CallEvidence;
import com.flux.service.model.PersonDetails;
import com.flux.service.model.RegisterWebhookCommand;
import com.flux.service.model.RegisterWebhookResult;
import com.flux.service.event.DomainEvent;
import com.flux.service.workflow.trigger.WorkflowTriggerRouter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowTriggerEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flux")
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
        registry.add("workflow.step-http.connect-timeout-ms", () -> "200");
        registry.add("workflow.step-http.read-timeout-ms", () -> "200");
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
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowTriggerRouter workflowTriggerRouter;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WorkflowRunStepRepository workflowRunStepRepository;

    @Autowired
    private WorkflowRunStepClaimRepository workflowRunStepClaimRepository;

    @Autowired
    private WorkflowStepExecutionService workflowStepExecutionService;

    @Autowired
    private AutomationWorkflowRepository automationWorkflowRepository;

    @Autowired
    private MutableTestClock testClock;

    @Autowired
    private FollowUpBossClient followUpBossClient;

    private StubFollowUpBossClient stubFollowUpBossClient;
    private HttpServer httpServer;
    private String slackEndpoint;
    private final AtomicInteger slackRequestCount = new AtomicInteger();
    private final AtomicReference<List<Integer>> slackResponsePlan = new AtomicReference<>(new ArrayList<>());

    @BeforeEach
    void setUp() throws IOException {
        workflowRunStepRepository.deleteAll();
        workflowRunRepository.deleteAll();
        automationWorkflowRepository.deleteAll();

        testClock.set(Instant.parse("2026-02-01T10:00:00Z"));

        if (!(followUpBossClient instanceof StubFollowUpBossClient client)) {
            throw new IllegalStateException("Expected StubFollowUpBossClient");
        }
        this.stubFollowUpBossClient = client;
        this.stubFollowUpBossClient.reset();

        startSlackServer();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void matchingWebhook_createsRun_executesTerminally() {
        seedActiveWorkflow("WF_WAVE3_E2E_MATCH", triggerConfig("PERSON", "UPDATED", "event.payload.channel = \"zillow\""),
                workflowGraph(slackEndpoint, false));
        slackResponsePlan.set(List.of(200));

        workflowTriggerRouter.route(domainEvent("zillow", 777L));

        WorkflowRunEntity run = singleWorkflowRun();
        assertEquals("777", run.getSourcePersonId());
        assertNull(run.getWebhookEventId(), "webhookEventId is null when the domain event has no source webhook");

        executeUntilRunTerminal(run.getId(), Duration.ofSeconds(5));

        WorkflowRunEntity terminal = workflowRunRepository.findById(run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, terminal.getStatus());
        assertEquals("ACTION_COMPLETED", terminal.getReasonCode());

        WorkflowRunStepEntity addTagStep = findStep(run.getId(), "tag_1");
        WorkflowRunStepEntity notifyStep = findStep(run.getId(), "notify_1");
        assertEquals(WorkflowRunStepStatus.COMPLETED, addTagStep.getStatus());
        assertEquals("SUCCESS", addTagStep.getResultCode());
        assertEquals(WorkflowRunStepStatus.COMPLETED, notifyStep.getStatus());
        assertEquals("SUCCESS", notifyStep.getResultCode());
        assertEquals(1, stubFollowUpBossClient.addTagCalls().size());
        assertEquals(1, slackRequestCount.get());
    }

    @Test
    void nonMatchingWebhook_createsNoWorkflowRun() {
        seedActiveWorkflow("WF_WAVE3_E2E_NON_MATCH", triggerConfig("PERSON", "UPDATED", "event.payload.channel = \"zillow\""),
                workflowGraph(slackEndpoint, false));

        workflowTriggerRouter.route(domainEvent("manual", 888L));

        assertEquals(0, workflowRunRepository.count(), "Non-matching event must not plan workflow run");
        assertEquals(0, workflowRunStepRepository.count(), "No workflow run steps should be materialized");
    }

    @Test
    void notificationTransientFailure_thenRetrySuccess_completesRun() {
        seedActiveWorkflow("WF_WAVE3_E2E_RETRY", triggerConfig("PERSON", "UPDATED", "event.payload.channel = \"zillow\""),
                workflowGraph(slackEndpoint, true));
        slackResponsePlan.set(List.of(503, 200));

        workflowTriggerRouter.route(domainEvent("zillow", 999L));

        WorkflowRunEntity run = singleWorkflowRun();
        executeUntilRunTerminal(run.getId(), Duration.ofSeconds(5));

        WorkflowRunEntity terminal = workflowRunRepository.findById(run.getId()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, terminal.getStatus());
        assertEquals("ACTION_COMPLETED", terminal.getReasonCode());

        WorkflowRunStepEntity notifyStep = findStep(run.getId(), "notify_1");
        assertEquals(WorkflowRunStepStatus.COMPLETED, notifyStep.getStatus());
        assertEquals("SUCCESS", notifyStep.getResultCode());
        assertEquals(1, notifyStep.getRetryCount());
        assertEquals(2, slackRequestCount.get());
        assertTrue(notifyStep.getErrorMessage() == null || !notifyStep.getErrorMessage().contains("http://localhost"));
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

    private WorkflowRunEntity singleWorkflowRun() {
        List<WorkflowRunEntity> runs = workflowRunRepository.findAll();
        assertEquals(1, runs.size(), "Expected exactly one workflow run");
        return runs.getFirst();
    }

    private WorkflowRunStepEntity findStep(Long runId, String nodeId) {
        return workflowRunStepRepository.findByRunIdAndNodeId(runId, nodeId)
                .orElseThrow(() -> new AssertionError("Missing workflow step nodeId=" + nodeId));
    }

    private void seedActiveWorkflow(String key, Map<String, Object> triggerConfig, Map<String, Object> graph) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName("Workflow " + key);
        entity.setStatus(WorkflowStatus.ACTIVE);
        entity.setTrigger(triggerConfig);
        entity.setGraph(graph);
        automationWorkflowRepository.saveAndFlush(entity);
    }

    private Map<String, Object> triggerConfig(String eventDomain, String eventAction, String filter) {
        return Map.of("on", "person.state_changed", "filter", filter);
    }

    private Map<String, Object> workflowGraph(String webhookUrl, boolean withRetryOverride) {
        Map<String, Object> notifyConfig = withRetryOverride
                ? Map.of(
                        "webhookUrl", webhookUrl,
                        "text", "Workflow completed",
                        "retryPolicy", Map.of(
                                "maxAttempts", 3,
                                "initialBackoffMs", 10,
                                "backoffMultiplier", 1.0,
                                "maxBackoffMs", 10,
                                "retryOnTransient", true))
                : Map.of(
                        "webhookUrl", webhookUrl,
                        "text", "Workflow completed");

        return Map.of(
                "schemaVersion", 1,
                "entryNode", "tag_1",
                "nodes", List.of(
                        Map.of(
                                "id", "tag_1",
                                "type", "fub_add_tag",
                                "config", Map.of("tagName", "Wave3"),
                                "transitions", Map.of(
                                        "SUCCESS", List.of("notify_1"),
                                        "FAILED", Map.of("terminal", "ACTION_FAILED"))),
                        Map.of(
                                "id", "notify_1",
                                "type", "slack_notify",
                                "config", notifyConfig,
                                "transitions", Map.of(
                                        "SUCCESS", Map.of("terminal", "ACTION_COMPLETED"),
                                        "FAILED", Map.of("terminal", "ACTION_FAILED")))));
    }

    private DomainEvent domainEvent(String channel, long leadId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("channel", channel);
        // id null → run.domainEventId null (no events row to FK against in this test);
        // dedup falls back to workflowKey + source + sourcePersonId, unique per test.
        return new DomainEvent(null, "person.state_changed", "FUB", null, "person", String.valueOf(leadId), payload);
    }

    private void startSlackServer() throws IOException {
        slackRequestCount.set(0);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/slack", exchange -> {
            slackRequestCount.incrementAndGet();
            List<Integer> plan = slackResponsePlan.get();
            int index = Math.max(0, slackRequestCount.get() - 1);
            int status = index < plan.size() ? plan.get(index) : 200;

            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        httpServer.start();
        slackEndpoint = "http://localhost:" + httpServer.getAddress().getPort() + "/slack";
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

        List<String> addTagCalls() {
            return addTagCalls;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, command == null ? null : command.event(), command == null ? null : command.url(), "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            throw new UnsupportedOperationException("Not used in workflow trigger E2E tests");
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            throw new UnsupportedOperationException("Not used in workflow trigger E2E tests");
        }

        @Override
        public JsonNode getPersonRawById(long personId) {
            throw new UnsupportedOperationException("Not used in workflow trigger E2E tests");
        }

        @Override
        public List<CallEvidence> listPersonCalls(long personId) {
            return List.of();
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
            throw new UnsupportedOperationException("Not used in workflow trigger E2E tests");
        }

        @Override
        public CreatedNote createNote(CreateNoteCommand command) {
            throw new UnsupportedOperationException("createNote not used in this test stub");
        }
    }
}
