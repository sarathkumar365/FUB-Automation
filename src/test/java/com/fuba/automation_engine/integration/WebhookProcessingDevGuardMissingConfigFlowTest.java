package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "rules.call-outcome.dev-test-user-id=0")
class WebhookProcessingDevGuardMissingConfigFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
    }

    @Test
    void shouldSkipWhenDevTestUserNotConfigured() throws Exception {
        String body = """
                {
                  "eventId": "evt-dev-missing-1",
                  "event": "callsCreated",
                  "resourceIds": [1101],
                  "uri": null
                }
                """;
        String signature = hmacHex(base64(body), "test-signing-key");

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", signature)
                        .content(body))
                .andExpect(status().isAccepted());

        ProcessedCallEntity entity = waitForCall(1101L);
        assertEquals(ProcessedCallStatus.SKIPPED, entity.getStatus());
        assertEquals("DEV_MODE_TEST_USER_NOT_CONFIGURED", entity.getFailureReason());
    }

    private ProcessedCallEntity waitForCall(Long callId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
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
        FollowUpBossClient followUpBossClient() {
            return new FollowUpBossClient() {
                @Override
                public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
                    return new RegisterWebhookResult(0L, null, null, "STUBBED");
                }

                @Override
                public CallDetails getCallById(long callId) {
                    return new CallDetails(callId, 44L, 0, 20L, "No Answer");
                }

                @Override
                public PersonDetails getPersonById(long personId) {
                    return new PersonDetails(personId, null, null, null);
                }

                @Override
                public JsonNode getPersonRawById(long personId) {
                    throw new UnsupportedOperationException("Not used in dev guard missing config tests");
                }

                @Override
                public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
                    return new PersonCommunicationCheckResult(personId, false);
                }

                @Override
                public CreatedTask createTask(CreateTaskCommand command) {
                    return new CreatedTask(0L, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
                }

                @Override
                public ActionExecutionResult reassignPerson(long personId, long targetUserId) {
                    return new ActionExecutionResult(true, "STUBBED", null);
                }

                @Override
                public ActionExecutionResult movePersonToPond(long personId, long targetPondId) {
                    return new ActionExecutionResult(true, "STUBBED", null);
                }

                @Override
                public ActionExecutionResult addTag(long personId, String tagName) {
                    return new ActionExecutionResult(true, "STUBBED", null);
                }
            };
        }
    }
}
