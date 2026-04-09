package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "rules.call-outcome.dev-test-user-id=20")
class WebhookProcessingDevGuardFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private DevGuardFollowUpBossClient followUpBossClient;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        followUpBossClient.reset();
    }

    @Test
    void shouldCreateTaskWhenAssigneeMatchesDevTestUser() throws Exception {
        followUpBossClient.setCallDetails(1001L, new CallDetails(1001L, 9L, 0, 20L, "No Answer"));

        sendWebhook("evt-dev-1", "[1001]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(1001L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(1, followUpBossClient.createdTasks().size());
    }

    @Test
    void shouldSkipTaskWhenAssigneeDoesNotMatchDevTestUser() throws Exception {
        followUpBossClient.setCallDetails(1002L, new CallDetails(1002L, 9L, 0, 30L, "No Answer"));

        sendWebhook("evt-dev-2", "[1002]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(1002L);
        assertEquals(ProcessedCallStatus.SKIPPED, processedCall.getStatus());
        assertEquals("DEV_MODE_USER_FILTERED", processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty());
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
        Optional<ProcessedCallEntity> current = Optional.empty();
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
        DevGuardFollowUpBossClient devGuardFollowUpBossClient() {
            return new DevGuardFollowUpBossClient();
        }
    }

    static class DevGuardFollowUpBossClient implements FollowUpBossClient {
        private final Map<Long, CallDetails> callDetails = new HashMap<>();
        private final CopyOnWriteArrayList<CreateTaskCommand> createdTasks = new CopyOnWriteArrayList<>();
        private volatile long taskSequence = 7000L;

        void reset() {
            callDetails.clear();
            createdTasks.clear();
            taskSequence = 7000L;
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
        public CreatedTask createTask(CreateTaskCommand command) {
            createdTasks.add(command);
            return new CreatedTask(taskSequence++, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
        }
    }
}
