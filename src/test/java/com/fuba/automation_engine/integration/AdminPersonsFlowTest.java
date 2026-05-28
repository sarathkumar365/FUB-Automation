package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.PersonEntity;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.repository.PersonRepository;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.service.FollowUpBossClient;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.CreateNoteCommand;
import com.fuba.automation_engine.service.model.CreatedNote;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.CallEvidence;
import java.util.List;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin-test", roles = "ADMIN")
class AdminPersonsFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubFollowUpBossClient stubFollowUpBossClient;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        workflowRunRepository.deleteAll();
        webhookEventRepository.deleteAll();
        personRepository.deleteAll();
        stubFollowUpBossClient.reset();
    }

    @Test
    void listEndpoint_shouldReturnPersonsOrderedByUpdatedAtDesc() throws Exception {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-15T10:00:00Z");
        savePerson("FUB", "L-101", base);
        savePerson("FUB", "L-102", base.plusMinutes(5));

        MvcResult result = mockMvc.perform(get("/admin/persons").param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, body.get("items").size());
        assertEquals("L-102", body.get("items").get(0).get("sourcePersonId").asText());
        assertEquals("L-101", body.get("items").get(1).get("sourcePersonId").asText());
        assertNotNull(body.get("serverTime"));
    }

    @Test
    void listEndpoint_shouldPaginateViaCursor() throws Exception {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-15T11:00:00Z");
        savePerson("FUB", "L-A", base.minusMinutes(2));
        savePerson("FUB", "L-B", base.minusMinutes(1));
        savePerson("FUB", "L-C", base);

        MvcResult page1 = mockMvc.perform(get("/admin/persons").param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page1Body = objectMapper.readTree(page1.getResponse().getContentAsString());
        assertEquals(2, page1Body.get("items").size());
        String cursor = page1Body.get("nextCursor").asText();
        assertTrue(cursor != null && !cursor.isBlank());

        MvcResult page2 = mockMvc.perform(get("/admin/persons")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode page2Body = objectMapper.readTree(page2.getResponse().getContentAsString());
        assertEquals(1, page2Body.get("items").size());
        assertEquals("L-A", page2Body.get("items").get(0).get("sourcePersonId").asText());
    }

    @Test
    void listEndpoint_shouldReject400WhenFromAfterTo() throws Exception {
        mockMvc.perform(get("/admin/persons")
                        .param("from", "2026-04-15T11:00:00Z")
                        .param("to", "2026-04-15T10:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryEndpoint_shouldAggregateLocalTimelineWithoutLiveRefresh() throws Exception {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-16T09:00:00Z");
        savePerson("FUB", "42", base);
        saveCall(9001L, "42", base.minusMinutes(5));
        saveRun("42", "idem-1", base.minusMinutes(10));

        mockMvc.perform(get("/admin/persons/42/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveStatus").value("LIVE_SKIPPED"))
                .andExpect(jsonPath("$.person.sourcePersonId").value("42"))
                .andExpect(jsonPath("$.recentCalls.length()").value(1))
                .andExpect(jsonPath("$.recentWorkflowRuns.length()").value(1))
                .andExpect(jsonPath("$.activity.length()").value(2));
    }

    @Test
    void summaryEndpoint_shouldFallBackToLocalWhenLiveRefreshFails() throws Exception {
        savePerson("FUB", "77", OffsetDateTime.parse("2026-04-16T10:00:00Z"));
        stubFollowUpBossClient.failNext();

        mockMvc.perform(get("/admin/persons/77/summary").param("includeLive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveStatus").value("LIVE_FAILED"))
                .andExpect(jsonPath("$.liveMessage").exists())
                .andExpect(jsonPath("$.person.sourcePersonId").value("77"));

        assertTrue(stubFollowUpBossClient.getPersonCalledFor(77L));
    }

    @Test
    void summaryEndpoint_shouldReturn404WhenPersonMissing() throws Exception {
        mockMvc.perform(get("/admin/persons/does-not-exist/summary"))
                .andExpect(status().isNotFound());
    }

    private void savePerson(String sourceSystem, String sourcePersonId, OffsetDateTime updatedAt) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", "Person " + sourcePersonId);
        snapshot.put("stage", "Person");

        PersonEntity entity = new PersonEntity();
        entity.setSourceSystem(sourceSystem);
        entity.setSourcePersonId(sourcePersonId);
        entity.setStatus(PersonStatus.ACTIVE);
        entity.setPersonDetails(snapshot);
        entity.setCreatedAt(updatedAt);
        entity.setUpdatedAt(updatedAt);
        entity.setLastSyncedAt(updatedAt);
        personRepository.saveAndFlush(entity);
    }

    private void saveCall(Long callId, String sourcePersonId, OffsetDateTime startedAt) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRetryCount(0);
        entity.setSourcePersonId(sourcePersonId);
        entity.setIsIncoming(true);
        entity.setDurationSeconds(42);
        entity.setOutcome("Connected");
        entity.setCallStartedAt(startedAt);
        entity.setCreatedAt(startedAt);
        entity.setUpdatedAt(startedAt);
        processedCallRepository.saveAndFlush(entity);
    }

    private void saveRun(String sourcePersonId, String idempotencyKey, OffsetDateTime createdAt) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowId(1L);
        run.setWorkflowKey("WF_ADMIN_LEADS_TEST");
        run.setWorkflowVersion(1L);
        run.setWorkflowGraphSnapshot(java.util.Map.of("schemaVersion", 1, "entryNode", "n1", "nodes", java.util.List.of()));
        run.setSource("FUB");
        run.setEventId("evt-" + idempotencyKey);
        run.setSourcePersonId(sourcePersonId);
        run.setStatus(WorkflowRunStatus.COMPLETED);
        run.setReasonCode("DONE");
        run.setIdempotencyKey(idempotencyKey);
        workflowRunRepository.saveAndFlush(run);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        StubFollowUpBossClient stubFollowUpBossClient() {
            return new StubFollowUpBossClient();
        }
    }

    static class StubFollowUpBossClient implements FollowUpBossClient {

        private final AtomicBoolean failNext = new AtomicBoolean(false);
        private final AtomicBoolean personCalled = new AtomicBoolean(false);
        private volatile long lastPersonId;

        void reset() {
            failNext.set(false);
            personCalled.set(false);
            lastPersonId = 0L;
        }

        void failNext() {
            failNext.set(true);
        }

        boolean getPersonCalledFor(long personId) {
            return personCalled.get() && lastPersonId == personId;
        }

        @Override
        public RegisterWebhookResult registerWebhook(RegisterWebhookCommand command) {
            return new RegisterWebhookResult(0L, null, null, "STUBBED");
        }

        @Override
        public CallDetails getCallById(long callId) {
            return new CallDetails(callId, 10L, 5, 20L, "Connected");
        }

        @Override
        public PersonDetails getPersonById(long personId) {
            return new PersonDetails(personId, null, null, null);
        }

        @Override
        public JsonNode getPersonRawById(long personId) {
            personCalled.set(true);
            lastPersonId = personId;
            if (failNext.get()) {
                throw new IllegalStateException("Simulated FUB outage for personId=" + personId);
            }
            ObjectNode node = new ObjectMapper().createObjectNode();
            node.put("id", personId);
            node.put("name", "Live Person " + personId);
            node.put("stage", "Person");
            return node;
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
            return ActionExecutionResult.ok();
        }

        @Override
        public CreatedTask createTask(CreateTaskCommand command) {
            return new CreatedTask(1L, command.personId(), command.assignedUserId(), command.name(), command.dueDate(), null);
        }

        @Override
        public CreatedNote createNote(CreateNoteCommand command) {
            throw new UnsupportedOperationException("createNote not used in this test stub");
        }
    }
}
