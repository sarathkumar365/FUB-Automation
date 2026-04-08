package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.exception.fub.FubPermanentException;
import com.fuba.automation_engine.exception.fub.FubTransientException;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.rules.CallDecisionEngine;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private TestFollowUpBossClient followUpBossClient;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        followUpBossClient.reset();
    }

    @Test
    void shouldCreateTaskForShortCall() throws Exception {
        followUpBossClient.setCallDetails(123L, new CallDetails(123L, 10L, 5, 20L, "Connected"));

        sendWebhook("evt-step4-1", "callsCreated", "[123]")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Webhook accepted for async processing"));

        ProcessedCallEntity processedCall = waitForCall(123L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.RULE_SHORT, processedCall.getRuleApplied());
        assertEquals(5000L, processedCall.getTaskId());
        assertNull(processedCall.getFailureReason());
        assertEquals(1, followUpBossClient.createdTasks().size());
    }

    @Test
    void shouldSkipWhenAssigneeMissing() throws Exception {
        followUpBossClient.setCallDetails(124L, new CallDetails(124L, 44L, 0, 0L, "No Answer"));

        sendWebhook("evt-step4-2", "callsCreated", "[124]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(124L);
        assertEquals(ProcessedCallStatus.SKIPPED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.REASON_MISSING_ASSIGNEE, processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty());
    }

    @Test
    void shouldSkipConnectedCallsOverThreshold() throws Exception {
        followUpBossClient.setCallDetails(125L, new CallDetails(125L, 44L, 31, 20L, "Connected"));

        sendWebhook("evt-step4-3", "callsCreated", "[125]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(125L);
        assertEquals(ProcessedCallStatus.SKIPPED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.REASON_CONNECTED_NO_FOLLOWUP, processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty());
    }

    @Test
    void shouldCreateTaskFromNoAnswerWhenDurationMissing() throws Exception {
        followUpBossClient.setCallDetails(126L, new CallDetails(126L, 44L, null, 20L, "No Answer"));

        sendWebhook("evt-step4-4", "callsCreated", "[126]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(126L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.RULE_OUTCOME_NO_ANSWER, processedCall.getRuleApplied());
        assertEquals(1, followUpBossClient.createdTasks().size());
    }

    @Test
    void shouldFailWhenDurationMissingAndOutcomeUnknown() throws Exception {
        followUpBossClient.setCallDetails(127L, new CallDetails(127L, 44L, null, 20L, "Connected"));

        sendWebhook("evt-step4-5", "callsCreated", "[127]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(127L);
        assertEquals(ProcessedCallStatus.FAILED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.REASON_UNMAPPED_OUTCOME_WITHOUT_DURATION, processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty());
    }

    @Test
    void shouldCreateGenericTaskWhenPersonMissingAndClientAccepts() throws Exception {
        followUpBossClient.setCallDetails(128L, new CallDetails(128L, 0L, 0, 20L, "No Answer"));

        sendWebhook("evt-step4-6", "callsCreated", "[128]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(128L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(1, followUpBossClient.createdTasks().size());
        assertNull(followUpBossClient.createdTasks().get(0).personId());
    }

    @Test
    void shouldFailWhenGenericTaskRejectedForMissingPerson() throws Exception {
        followUpBossClient.setCallDetails(129L, new CallDetails(129L, 0L, 0, 20L, "No Answer"));
        followUpBossClient.setRejectMissingPerson(true);

        sendWebhook("evt-step4-7", "callsCreated", "[129]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(129L);
        assertEquals(ProcessedCallStatus.FAILED, processedCall.getStatus());
        assertEquals("PERMANENT_TASK_CREATE_FAILURE:400", processedCall.getFailureReason());
        assertTrue(followUpBossClient.createdTasks().isEmpty());
    }

    @Test
    void shouldPersistUnsupportedCallsUpdatedWithoutDispatch() throws Exception {
        sendWebhook("evt-step4-8", "callsUpdated", "[555]")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Event type not supported yet: callsUpdated"));

        Optional<WebhookEventEntity> persistedEvent = webhookEventRepository.findBySourceAndEventId(
                WebhookSource.FUB,
                "evt-step4-8");
        assertTrue(persistedEvent.isPresent());
        assertEquals(EventSupportState.IGNORED, persistedEvent.orElseThrow().getCatalogState());
        assertTrue(processedCallRepository.findByCallId(555L).isEmpty());
        assertTrue(followUpBossClient.calledCallIds().isEmpty());
    }

    @Test
    void shouldPersistSupportedPeopleCreatedWithoutCallProcessingSideEffects() throws Exception {
        sendWebhook("evt-step4-11", "peopleCreated", "[556]")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Webhook accepted for async processing"));

        Optional<WebhookEventEntity> persistedEvent = webhookEventRepository.findBySourceAndEventId(
                WebhookSource.FUB,
                "evt-step4-11");
        assertTrue(persistedEvent.isPresent());
        assertEquals(EventSupportState.SUPPORTED, persistedEvent.orElseThrow().getCatalogState());
        assertTrue(processedCallRepository.findByCallId(556L).isEmpty());
        assertTrue(followUpBossClient.calledCallIds().isEmpty());
    }

    @Test
    void shouldProcessMultipleResourceIdsIndependently() throws Exception {
        followUpBossClient.setCallDetails(901L, new CallDetails(901L, 10L, 0, 20L, "No Answer"));
        followUpBossClient.setCallDetails(902L, new CallDetails(902L, 10L, 31, 20L, "Connected"));

        sendWebhook("evt-step4-9", "callsCreated", "[901,902]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity call901 = waitForCall(901L);
        ProcessedCallEntity call902 = waitForCall(902L);

        assertEquals(ProcessedCallStatus.TASK_CREATED, call901.getStatus());
        assertEquals(ProcessedCallStatus.SKIPPED, call902.getStatus());
        assertEquals(2, processedCallRepository.findAll().size());
    }

    @Test
    void shouldCreateTaskForNoAnswerEvenWhenDurationAboveThreshold() throws Exception {
        followUpBossClient.setCallDetails(903L, new CallDetails(903L, 10L, 43, 20L, "No Answer"));

        sendWebhook("evt-step4-10", "callsCreated", "[903]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(903L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(CallDecisionEngine.RULE_OUTCOME_NO_ANSWER, processedCall.getRuleApplied());
        assertEquals(1, followUpBossClient.createdTasks().size());
    }

    @Test
    void shouldRetryTransientFetchFailureBeforeSuccess() throws Exception {
        followUpBossClient.setCallDetails(904L, new CallDetails(904L, 10L, 0, 20L, "No Answer"));
        followUpBossClient.setFetchTransientFailures(904L, 2, 503);

        sendWebhook("evt-step5-1", "callsCreated", "[904]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(904L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(2, processedCall.getRetryCount());
        assertEquals(3, followUpBossClient.getCallAttemptsFor(904L));
    }

    @Test
    void shouldFailWhenTransientFetchRetriesExhausted() throws Exception {
        followUpBossClient.setFetchTransientFailures(905L, 3, 503);

        sendWebhook("evt-step5-2", "callsCreated", "[905]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(905L);
        assertEquals(ProcessedCallStatus.FAILED, processedCall.getStatus());
        assertEquals("TRANSIENT_FETCH_FAILURE:503", processedCall.getFailureReason());
        assertEquals(2, processedCall.getRetryCount());
        assertEquals(3, followUpBossClient.getCallAttemptsFor(905L));
    }

    @Test
    void shouldRetryTransientTaskCreateFailureBeforeSuccess() throws Exception {
        followUpBossClient.setCallDetails(906L, new CallDetails(906L, 10L, 0, 20L, "No Answer"));
        followUpBossClient.setCreateTaskTransientFailures(2, 429);

        sendWebhook("evt-step5-3", "callsCreated", "[906]")
                .andExpect(status().isAccepted());

        ProcessedCallEntity processedCall = waitForCall(906L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, processedCall.getStatus());
        assertEquals(2, processedCall.getRetryCount());
        assertEquals(3, followUpBossClient.createTaskAttempts());
    }

    private org.springframework.test.web.servlet.ResultActions sendWebhook(String eventId, String event, String resourceIds)
            throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "event": "%s",
                  "resourceIds": %s,
                  "uri": null
                }
                """.formatted(eventId, event, resourceIds);

        return mockMvc.perform(post("/webhooks/fub")
                .contentType(MediaType.APPLICATION_JSON)
                .header("FUB-Signature", hmacHex(base64(body), "test-signing-key"))
                .content(body));
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
        private final Map<Long, AtomicInteger> fetchAttempts = new HashMap<>();
        private final Map<Long, Integer> fetchTransientFailuresRemaining = new HashMap<>();
        private final List<Long> calledCallIds = new CopyOnWriteArrayList<>();
        private final List<CreateTaskCommand> createdTasks = new CopyOnWriteArrayList<>();
        private final AtomicInteger createTaskAttempts = new AtomicInteger(0);
        private volatile int createTaskTransientFailuresRemaining;
        private volatile int createTaskTransientStatusCode = 503;
        private volatile int fetchTransientStatusCode = 503;
        private volatile boolean rejectMissingPerson;
        private volatile long taskSequence = 5000L;

        void reset() {
            callDetails.clear();
            calledCallIds.clear();
            createdTasks.clear();
            fetchAttempts.clear();
            fetchTransientFailuresRemaining.clear();
            createTaskAttempts.set(0);
            createTaskTransientFailuresRemaining = 0;
            createTaskTransientStatusCode = 503;
            fetchTransientStatusCode = 503;
            rejectMissingPerson = false;
            taskSequence = 5000L;
        }

        void setCallDetails(Long callId, CallDetails details) {
            callDetails.put(callId, details);
        }

        void setRejectMissingPerson(boolean rejectMissingPerson) {
            this.rejectMissingPerson = rejectMissingPerson;
        }

        void setFetchTransientFailures(Long callId, int failures, int statusCode) {
            fetchTransientFailuresRemaining.put(callId, failures);
            fetchTransientStatusCode = statusCode;
        }

        void setCreateTaskTransientFailures(int failures, int statusCode) {
            createTaskTransientFailuresRemaining = failures;
            createTaskTransientStatusCode = statusCode;
        }

        List<Long> calledCallIds() {
            return calledCallIds;
        }

        List<CreateTaskCommand> createdTasks() {
            return createdTasks;
        }

        int getCallAttemptsFor(Long callId) {
            return fetchAttempts.getOrDefault(callId, new AtomicInteger(0)).get();
        }

        int createTaskAttempts() {
            return createTaskAttempts.get();
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, null, null, "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            calledCallIds.add(callId);
            fetchAttempts.computeIfAbsent(callId, ignored -> new AtomicInteger(0)).incrementAndGet();
            Integer remainingFailures = fetchTransientFailuresRemaining.get(callId);
            if (remainingFailures != null && remainingFailures > 0) {
                fetchTransientFailuresRemaining.put(callId, remainingFailures - 1);
                throw new FubTransientException("Simulated transient fetch failure", fetchTransientStatusCode);
            }
            return callDetails.getOrDefault(callId, new CallDetails(callId, 10L, 5, 20L, "Connected"));
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            return new PersonDetails(personId, null, null);
        }

        @Override
        public PersonCommunicationCheckResult checkPersonCommunication(long personId) {
            return new PersonCommunicationCheckResult(personId, false);
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            createTaskAttempts.incrementAndGet();
            if (createTaskTransientFailuresRemaining > 0) {
                createTaskTransientFailuresRemaining--;
                throw new FubTransientException("Simulated transient create failure", createTaskTransientStatusCode);
            }
            if (rejectMissingPerson && command.personId() == null) {
                throw new FubPermanentException("Missing personId for task", 400);
            }
            createdTasks.add(command);
            return new CreatedTask(taskSequence++, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
        }
    }
}
