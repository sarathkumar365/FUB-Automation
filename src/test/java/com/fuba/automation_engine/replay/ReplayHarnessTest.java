package com.fuba.automation_engine.replay;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.LeadRepository;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 0 replay harness.
 *
 * <p>Drives recorded webhook sequences through the live engine and asserts on
 * downstream effects (workflow runs created, FUB calls attempted, webhook rows
 * persisted). Fixtures live under {@code src/test/resources/replay-fixtures/}
 * and are loaded automatically by {@link ReplayFixtureLoader}.
 *
 * <p>Each fixture becomes a dynamic test via {@link TestFactory}. Failures
 * identify the fixture by name. To add a new scenario, drop a new JSON file
 * under {@code replay-fixtures/} matching the {@link ReplayFixture} shape —
 * no Java changes required.
 *
 * <p>This harness deliberately uses the same in-memory H2 + MockMvc setup as
 * the existing webhook integration tests (rather than Testcontainers) for
 * fast startup. Phase 5 will revisit when the partial unique index lands and
 * H2's lack of partial-index support becomes blocking.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReplayHarnessTest {

    /** Workflow registered at setup so webhooks have something to trigger. */
    private static final String TEST_WORKFLOW_KEY = "replay_harness_test_workflow";

    /** Matches the test signing key in {@code src/test/resources/application.properties}. */
    private static final String SIGNING_KEY = "test-signing-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomationWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private ReplayHarnessFollowUpBossClient fubClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        workflowRunRepository.deleteAll();
        webhookEventRepository.deleteAll();
        workflowRepository.deleteAll();
        leadRepository.deleteAll();
        fubClient.reset();
        seedTestWorkflow();
    }

    @TestFactory
    @DisplayName("Replay fixtures from src/test/resources/replay-fixtures/")
    Iterable<DynamicTest> replayAllFixtures() {
        List<ReplayFixture> fixtures = ReplayFixtureLoader.loadAll();
        return fixtures.stream()
                .map(fixture -> DynamicTest.dynamicTest(fixture.name(), () -> runFixture(fixture)))
                .toList();
    }

    private void runFixture(ReplayFixture fixture) throws Exception {
        // Script per-lead FUB person snapshots BEFORE driving webhooks so the
        // upsert path sees real-looking data.
        if (fixture.personSnapshots() != null) {
            fixture.personSnapshots()
                    .forEach((idStr, snapshot) -> fubClient.setPersonSnapshot(Long.parseLong(idStr), snapshot));
        }

        Instant scenarioStart = Instant.now();
        for (ReplayFixture.ReplayEvent event : fixture.events()) {
            sleepUntil(scenarioStart.plus(Duration.ofMillis(event.deltaMs())));
            postWebhook(event);
        }

        long drainTimeoutMs = fixture.drainTimeoutMsOrDefault();
        ReplayFixture.Expected expected = fixture.expected();
        if (expected == null) {
            return; // fixture is recorded-only; no assertions
        }

        // Wait for the expected effects to materialise, then assert.
        Instant deadline = Instant.now().plus(Duration.ofMillis(drainTimeoutMs));
        while (Instant.now().isBefore(deadline) && !expectationsMet(expected)) {
            Thread.sleep(50);
        }
        assertExpectations(fixture, expected);
        assertPhase1Invariants(fixture);
    }

    /**
     * Phase 1 (domain-events) invariants that must hold for every fixture once
     * the foundation work has landed. Independent of the fixture's own
     * {@code expected} block.
     */
    private void assertPhase1Invariants(ReplayFixture fixture) {
        // I5 / known-issue #25: every workflow_run created from a webhook must
        // carry the proximate webhook_events.id so investigations can correlate
        // cleanly without time-window matching.
        workflowRunRepository.findAll().forEach(run -> assertTrue(
                run.getWebhookEventId() != null,
                "fixture=" + fixture.name()
                        + " expected workflow_runs.webhook_event_id non-null for runId="
                        + run.getId()
                        + " sourceLeadId=" + run.getSourceLeadId()
                        + " — Phase 1 foundation must populate this from the trigger router."));
    }

    private boolean expectationsMet(ReplayFixture.Expected expected) {
        if (expected.minWebhookEvents() != null
                && webhookEventRepository.count() < expected.minWebhookEvents()) {
            return false;
        }
        if (expected.minWorkflowRunsForLead() != null) {
            for (Map.Entry<String, Integer> entry : expected.minWorkflowRunsForLead().entrySet()) {
                long actual = workflowRunRepository.findAll().stream()
                        .filter(run -> entry.getKey().equals(run.getSourceLeadId()))
                        .count();
                if (actual < entry.getValue()) {
                    return false;
                }
            }
        }
        if (expected.minReassignCalls() != null
                && fubClient.reassignCalls().size() < expected.minReassignCalls()) {
            return false;
        }
        if (expected.minCreateNoteCalls() != null
                && fubClient.createNoteCalls().size() < expected.minCreateNoteCalls()) {
            return false;
        }
        return true;
    }

    private void assertExpectations(ReplayFixture fixture, ReplayFixture.Expected expected) {
        String prefix = "fixture=" + fixture.name() + " ";

        if (expected.minWebhookEvents() != null) {
            long actual = webhookEventRepository.count();
            assertTrue(actual >= expected.minWebhookEvents(),
                    prefix + "expected >=" + expected.minWebhookEvents()
                            + " webhook_events rows, saw " + actual);
        }
        if (expected.minWorkflowRunsForLead() != null) {
            for (Map.Entry<String, Integer> entry : expected.minWorkflowRunsForLead().entrySet()) {
                long actual = workflowRunRepository.findAll().stream()
                        .filter(run -> entry.getKey().equals(run.getSourceLeadId()))
                        .count();
                assertTrue(actual >= entry.getValue(),
                        prefix + "expected >=" + entry.getValue()
                                + " workflow_runs for lead=" + entry.getKey()
                                + ", saw " + actual);
            }
        }
        if (expected.minReassignCalls() != null) {
            int actual = fubClient.reassignCalls().size();
            assertTrue(actual >= expected.minReassignCalls(),
                    prefix + "expected >=" + expected.minReassignCalls()
                            + " reassignPerson calls, saw " + actual);
        }
        if (expected.minCreateNoteCalls() != null) {
            int actual = fubClient.createNoteCalls().size();
            assertTrue(actual >= expected.minCreateNoteCalls(),
                    prefix + "expected >=" + expected.minCreateNoteCalls()
                            + " createNote calls, saw " + actual);
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private void postWebhook(ReplayFixture.ReplayEvent event) throws Exception {
        String resourceIdsJson = objectMapper.writeValueAsString(event.resourceIds());
        String body = """
                {
                  "eventId": "%s",
                  "event": "%s",
                  "resourceIds": %s,
                  "uri": null
                }
                """.formatted(event.eventId(), event.event(), resourceIdsJson);

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", hmacHex(base64(body), SIGNING_KEY))
                        .content(body));
    }

    private void seedTestWorkflow() {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(TEST_WORKFLOW_KEY);
        entity.setName("Replay Harness Test Workflow");
        entity.setStatus(WorkflowStatus.ACTIVE);
        entity.setTrigger(Map.of(
                "type", "webhook_fub",
                "config", Map.of(
                        "eventDomain", "LEAD",
                        "eventAction", "UPDATED")));
        entity.setGraph(Map.of(
                "schemaVersion", 1,
                "entryNode", "noop",
                "nodes", List.of(
                        Map.of(
                                "id", "noop",
                                "type", "branch_on_field",
                                "config", Map.of(
                                        "expression", "true",
                                        "resultMapping", Map.of("true", "DONE")),
                                "transitions", Map.of(
                                        "DONE", Map.of("terminal", "COMPLETED"))))));
        workflowRepository.saveAndFlush(entity);
    }

    private static void sleepUntil(Instant target) throws InterruptedException {
        long delta = Duration.between(Instant.now(), target).toMillis();
        if (delta > 0) {
            Thread.sleep(delta);
        }
    }

    private static String hmacHex(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String base64(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class TestConfig {

        /**
         * Declaring the bean with the concrete type works for both injection points:
         * Spring registers it under the concrete class AND under the {@link FollowUpBossClient}
         * interface, and {@code @Primary} resolves the conflict against the real
         * {@code FubFollowUpBossClient}.
         */
        @Bean
        @Primary
        ReplayHarnessFollowUpBossClient replayHarnessFollowUpBossClient() {
            return new ReplayHarnessFollowUpBossClient();
        }
    }
}
