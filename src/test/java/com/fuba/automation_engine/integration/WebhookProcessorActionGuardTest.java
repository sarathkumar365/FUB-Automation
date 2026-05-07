package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateNoteCommand;
import com.fuba.automation_engine.service.model.CreatedNote;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the kill-switch behaviour from the
 * {@code disable-hardcoded-task-creation} feature: when
 * {@code rules.call-outcome.task-creation-enabled=false}, a webhook
 * that would normally produce a {@code CREATE_TASK} decision must
 * land in {@code SKIPPED} state with reason {@code TASK_CREATION_DISABLED},
 * and no FUB outbound task call must be made.
 *
 * <p>The active profile is intentionally left blank (not "local") so we
 * verify the switch works irrespective of the local-profile dev guard;
 * the new check sits ahead of that guard in
 * {@code WebhookEventProcessorService.evaluateActionGuard}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "rules.call-outcome.task-creation-enabled=false")
class WebhookProcessorActionGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private ActionGuardFollowUpBossClient followUpBossClient;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        followUpBossClient.reset();
    }

    @Test
    void killSwitchSkipsTaskCreationAndRecordsReason() throws Exception {
        followUpBossClient.setCallDetails(2001L, new CallDetails(2001L, 9L, 0, 30L, "No Answer"));

        sendWebhook("evt-kill-1", "[2001]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(2001L);
        assertEquals(ProcessedCallStatus.SKIPPED, processedCall.getStatus());
        assertEquals("TASK_CREATION_DISABLED", processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty(),
                "FUB createTask must not be invoked when the kill switch is off");
    }

    private org.springframework.test.web.servlet.ResultActions sendWebhook(String eventId, String resourceIds) throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "event": "callsCreated",
                  "resourceIds": %s,
                  "uri": null
                }
                """.formatted(eventId, resourceIds);

        return mockMvc.perform(post("/webhooks/fub")
                .contentType(MediaType.APPLICATION_JSON)
                .header("FUB-Signature", hmacHex(base64(body), "test-signing-key"))
                .content(body));
    }

    private ProcessedCallEntity waitForCall(Long callId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        Optional<ProcessedCallEntity> current;
        while (Instant.now().isBefore(deadline)) {
            current = processedCallRepository.findByCallId(callId);
            if (current.isPresent()) {
                ProcessedCallEntity entity = current.get();
                if (entity.getStatus() == ProcessedCallStatus.FAILED
                        || entity.getStatus() == ProcessedCallStatus.SKIPPED
                        || entity.getStatus() == ProcessedCallStatus.TASK_CREATED) {
                    return entity;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected processed call row for callId=" + callId);
    }

    private String hmacHex(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String base64(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        ActionGuardFollowUpBossClient actionGuardFollowUpBossClient() {
            return new ActionGuardFollowUpBossClient();
        }
    }

    static class ActionGuardFollowUpBossClient implements FollowUpBossClient {
        private final Map<Long, CallDetails> callDetails = new HashMap<>();
        private final CopyOnWriteArrayList<CreateTaskCommand> createdTasks = new CopyOnWriteArrayList<>();
        private volatile long taskSequence = 8000L;

        void reset() {
            callDetails.clear();
            createdTasks.clear();
            taskSequence = 8000L;
        }

        void setCallDetails(Long callId, CallDetails details) {
            callDetails.put(callId, details);
        }

        CopyOnWriteArrayList<CreateTaskCommand> createdTasks() {
            return createdTasks;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, null, null, "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            return callDetails.getOrDefault(callId, new CallDetails(callId, 10L, 0, 20L, "No Answer"));
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            return new PersonDetails(personId, null, null, null);
        }

        @Override
        public JsonNode getPersonRawById(long personId) {
            throw new UnsupportedOperationException("Not used in action-guard flow tests");
        }

        @Override
        public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
            return new PersonCommunicationCheckResult(personId, false);
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
            return ActionExecutionResult.ok();
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            createdTasks.add(command);
            return new CreatedTask(taskSequence++, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
        }

        @Override
        public CreatedNote createNote(CreateNoteCommand command) {
            throw new UnsupportedOperationException("createNote not used in this test stub");
        }
    }
}
