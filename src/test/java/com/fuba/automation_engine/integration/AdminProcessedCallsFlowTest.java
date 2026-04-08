package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProcessedCallsFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private AdminFollowUpBossClient followUpBossClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        followUpBossClient.reset();
    }

    @Test
    void shouldListProcessedCallsWithFilters() throws Exception {
        saveProcessedCall(2001L, ProcessedCallStatus.FAILED, "TRANSIENT_FETCH_FAILURE:503", 2, OffsetDateTime.now().minusMinutes(1));
        saveProcessedCall(2002L, ProcessedCallStatus.TASK_CREATED, null, 0, OffsetDateTime.now());

        mockMvc.perform(get("/admin/processed-calls")
                        .param("status", "FAILED")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].callId").value(2001))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].retryCount").value(2));
    }

    @Test
    void shouldListProcessedCallsWithoutOptionalFilters() throws Exception {
        saveProcessedCall(2101L, ProcessedCallStatus.FAILED, "TRANSIENT_FETCH_FAILURE:503", 1, OffsetDateTime.now().minusMinutes(1));
        saveProcessedCall(2102L, ProcessedCallStatus.TASK_CREATED, null, 0, OffsetDateTime.now());

        mockMvc.perform(get("/admin/processed-calls")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldReplayFailedCall() throws Exception {
        saveProcessedCall(2003L, ProcessedCallStatus.FAILED, "TRANSIENT_TASK_CREATE_FAILURE:503", 1, OffsetDateTime.now());

        mockMvc.perform(post("/admin/processed-calls/2003/replay")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Replay accepted"));

        ProcessedCallEntity entity = waitForCall(2003L);
        assertEquals(ProcessedCallStatus.TASK_CREATED, entity.getStatus());
        assertTrue(followUpBossClient.createdTasks().size() >= 1);
    }

    @Test
    void shouldReturnNotFoundWhenReplayCallMissing() throws Exception {
        mockMvc.perform(post("/admin/processed-calls/9999/replay")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenReplayStatusNotFailed() throws Exception {
        saveProcessedCall(2004L, ProcessedCallStatus.SKIPPED, "CONNECTED_NO_FOLLOWUP", 0, OffsetDateTime.now());

        mockMvc.perform(post("/admin/processed-calls/2004/replay")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    private void saveProcessedCall(Long callId, ProcessedCallStatus status, String reason, int retryCount, OffsetDateTime updatedAt) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(status);
        entity.setFailureReason(reason);
        entity.setRetryCount(retryCount);
        entity.setCreatedAt(updatedAt.minusMinutes(2));
        entity.setUpdatedAt(updatedAt);
        entity.setRawPayload(objectMapper.createObjectNode());
        processedCallRepository.save(entity);
    }

    private ProcessedCallEntity waitForCall(Long callId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Optional<ProcessedCallEntity> current = Optional.empty();
        while (Instant.now().isBefore(deadline)) {
            current = processedCallRepository.findByCallId(callId);
            if (current.isPresent() && (current.get().getStatus() == ProcessedCallStatus.TASK_CREATED
                    || current.get().getStatus() == ProcessedCallStatus.FAILED
                    || current.get().getStatus() == ProcessedCallStatus.SKIPPED)) {
                return current.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected replayed call row for callId=" + callId);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        AdminFollowUpBossClient adminFollowUpBossClient() {
            return new AdminFollowUpBossClient();
        }
    }

    static class AdminFollowUpBossClient implements FollowUpBossClient {
        private final CopyOnWriteArrayList<CreateTaskCommand> createdTasks = new CopyOnWriteArrayList<>();

        void reset() {
            createdTasks.clear();
        }

        List<CreateTaskCommand> createdTasks() {
            return createdTasks;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, null, null, "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            return new CallDetails(callId, 50L, 0, 20L, "No Answer");
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            return new PersonDetails(personId, null, null);
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            createdTasks.add(command);
            return new CreatedTask(9000L, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
        }
    }
}
