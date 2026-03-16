package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookProcessingFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private TestFollowUpBossClient followUpBossClient;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        followUpBossClient.reset();
    }

    @Test
    void shouldProcessCallsCreatedAsFailedPendingRuleEngine() throws Exception {
        followUpBossClient.setCallDetails(123L, new CallDetails(123L, 10L, 5, 20L));

        String body = """
                {
                  "eventId": "evt-step3-1",
                  "event": "callsCreated",
                  "resourceIds": [123],
                  "uri": null
                }
                """;
        String signature = hmacHex(base64(body), "test-signing-key");

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Webhook accepted for async processing"));

        ProcessedCallEntity processedCall = waitForCall(123L);
        assertEquals(ProcessedCallStatus.FAILED, processedCall.getStatus());
        assertEquals("RULE_ENGINE_PENDING_STEP4", processedCall.getFailureReason());
    }

    @Test
    void shouldMarkUnsupportedCallsUpdatedAsFailedWithMessage() throws Exception {
        String body = """
                {
                  "eventId": "evt-step3-2",
                  "event": "callsUpdated",
                  "resourceIds": [555],
                  "uri": null
                }
                """;
        String signature = hmacHex(base64(body), "test-signing-key");

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Event type not supported yet: callsUpdated"));

        ProcessedCallEntity processedCall = waitForCall(555L);
        assertEquals(ProcessedCallStatus.FAILED, processedCall.getStatus());
        assertEquals("EVENT_TYPE_NOT_SUPPORTED_IN_STEP3:callsUpdated", processedCall.getFailureReason());
        assertTrue(followUpBossClient.calledCallIds().isEmpty());
    }

    @Test
    void shouldDeduplicateCallProcessingAcrossDuplicateEvents() throws Exception {
        followUpBossClient.setCallDetails(777L, new CallDetails(777L, 10L, 5, 20L));

        String body1 = """
                {"eventId":"evt-step3-3-a","event":"callsCreated","resourceIds":[777],"uri":null}
                """;
        String body2 = """
                {"eventId":"evt-step3-3-b","event":"callsCreated","resourceIds":[777],"uri":null}
                """;

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", hmacHex(base64(body1), "test-signing-key"))
                        .content(body1))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", hmacHex(base64(body2), "test-signing-key"))
                        .content(body2))
                .andExpect(status().isAccepted());

        waitForCall(777L);
        assertEquals(1, processedCallRepository.findAll().size());
    }

    @Test
    void shouldProcessMultipleResourceIdsIndependently() throws Exception {
        followUpBossClient.setCallDetails(901L, new CallDetails(901L, 10L, 5, 20L));
        followUpBossClient.setCallDetails(902L, new CallDetails(902L, 10L, 5, 20L));

        String body = """
                {"eventId":"evt-step3-4","event":"callsCreated","resourceIds":[901,902],"uri":null}
                """;

        mockMvc.perform(post("/webhooks/fub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("FUB-Signature", hmacHex(base64(body), "test-signing-key"))
                        .content(body))
                .andExpect(status().isAccepted());

        ProcessedCallEntity call901 = waitForCall(901L);
        ProcessedCallEntity call902 = waitForCall(902L);

        assertEquals(ProcessedCallStatus.FAILED, call901.getStatus());
        assertEquals(ProcessedCallStatus.FAILED, call902.getStatus());
        assertEquals(2, processedCallRepository.findAll().size());
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
        assertTrue(current.isPresent(), "Expected processed call row for callId=" + callId);
        return current.get();
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
        TestFollowUpBossClient testFollowUpBossClient() {
            return new TestFollowUpBossClient();
        }
    }

    static class TestFollowUpBossClient implements FollowUpBossClient {
        private final Map<Long, CallDetails> callDetails = new HashMap<>();
        private final List<Long> calledCallIds = new CopyOnWriteArrayList<>();

        void reset() {
            callDetails.clear();
            calledCallIds.clear();
        }

        void setCallDetails(Long callId, CallDetails details) {
            callDetails.put(callId, details);
        }

        List<Long> calledCallIds() {
            return calledCallIds;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, null, null, "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            calledCallIds.add(callId);
            return callDetails.getOrDefault(callId, new CallDetails(callId, 10L, 5, 20L));
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            return new CreatedTask(0L, command.personId(), command.assignedUserId(), command.name(), null, null);
        }
    }
}
