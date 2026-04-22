package com.fuba.automation_engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.repository.ProcessedCallRepository;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the three lead-scoped "top 10" derived query methods that feed the
 * admin leads detail timeline: {@link ProcessedCallRepository}
 * {@code findTop10BySourceLeadIdOrderByCallStartedAtDescIdDesc},
 * {@link WorkflowRunRepository}
 * {@code findTop10BySourceLeadIdOrderByCreatedAtDescIdDesc}, and
 * {@link WebhookEventRepository}
 * {@code findTop10BySourceLeadIdOrderByReceivedAtDescIdDesc}.
 */
@SpringBootTest
class LeadScopedTopNRepositoryTest {

    @Autowired
    private ProcessedCallRepository processedCallRepository;

    @Autowired
    private WorkflowRunRepository workflowRunRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processedCallRepository.deleteAll();
        workflowRunRepository.deleteAll();
        webhookEventRepository.deleteAll();
    }

    @Test
    void processedCalls_shouldReturnNewestFirstFilteredByLead() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-20T12:00:00Z");
        saveCall(3001L, "LEAD-1", now.minusMinutes(5));
        saveCall(3002L, "LEAD-1", now.minusMinutes(2));
        saveCall(3003L, "LEAD-1", now.minusMinutes(10));
        saveCall(3004L, "LEAD-2", now.minusMinutes(1));

        List<ProcessedCallEntity> calls =
                processedCallRepository.findTop10BySourceLeadIdOrderByCallStartedAtDescIdDesc("LEAD-1");

        assertEquals(3, calls.size());
        assertEquals(3002L, calls.get(0).getCallId());
        assertEquals(3001L, calls.get(1).getCallId());
        assertEquals(3003L, calls.get(2).getCallId());
    }

    @Test
    void processedCalls_shouldCapAtTenMostRecent() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-20T09:00:00Z");
        for (int i = 0; i < 12; i++) {
            saveCall(4000L + i, "LEAD-MANY", base.plusMinutes(i));
        }

        List<ProcessedCallEntity> calls =
                processedCallRepository.findTop10BySourceLeadIdOrderByCallStartedAtDescIdDesc("LEAD-MANY");

        assertEquals(10, calls.size());
        assertTrue(calls.get(0).getCallStartedAt().isAfter(calls.get(9).getCallStartedAt()));
    }

    @Test
    void workflowRuns_shouldReturnNewestFirstFilteredByLead() throws InterruptedException {
        WorkflowRunEntity r1 = saveRun("LEAD-1", "idem-a", WorkflowRunStatus.COMPLETED);
        Thread.sleep(5);
        WorkflowRunEntity r2 = saveRun("LEAD-1", "idem-b", WorkflowRunStatus.PENDING);
        Thread.sleep(5);
        WorkflowRunEntity r3 = saveRun("LEAD-2", "idem-c", WorkflowRunStatus.COMPLETED);

        List<WorkflowRunEntity> runs =
                workflowRunRepository.findTop10BySourceLeadIdOrderByCreatedAtDescIdDesc("LEAD-1");

        assertEquals(2, runs.size());
        assertEquals(r2.getId(), runs.get(0).getId());
        assertEquals(r1.getId(), runs.get(1).getId());
    }

    @Test
    void webhookEvents_shouldReturnNewestFirstFilteredByLead() {
        OffsetDateTime base = OffsetDateTime.parse("2026-04-20T08:00:00Z");
        WebhookEventEntity w1 = saveWebhook("evt-a", "LEAD-1", base);
        WebhookEventEntity w2 = saveWebhook("evt-b", "LEAD-1", base.plusMinutes(2));
        saveWebhook("evt-c", "LEAD-2", base.plusMinutes(5));

        List<WebhookEventEntity> events =
                webhookEventRepository.findTop10BySourceLeadIdOrderByReceivedAtDescIdDesc("LEAD-1");

        assertEquals(2, events.size());
        assertEquals(w2.getId(), events.get(0).getId());
        assertEquals(w1.getId(), events.get(1).getId());
    }

    private void saveCall(Long callId, String sourceLeadId, OffsetDateTime startedAt) {
        ProcessedCallEntity entity = new ProcessedCallEntity();
        entity.setCallId(callId);
        entity.setStatus(ProcessedCallStatus.RECEIVED);
        entity.setRetryCount(0);
        entity.setSourceLeadId(sourceLeadId);
        entity.setIsIncoming(true);
        entity.setDurationSeconds(10);
        entity.setOutcome("Connected");
        entity.setCallStartedAt(startedAt);
        entity.setCreatedAt(startedAt);
        entity.setUpdatedAt(startedAt);
        processedCallRepository.saveAndFlush(entity);
    }

    private WorkflowRunEntity saveRun(String sourceLeadId, String idempotencyKey, WorkflowRunStatus status) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowId(1L);
        run.setWorkflowKey("WF_TEST");
        run.setWorkflowVersion(1L);
        run.setWorkflowGraphSnapshot(java.util.Map.of("schemaVersion", 1, "entryNode", "n1", "nodes", java.util.List.of()));
        run.setSource("FUB");
        run.setEventId("evt-" + idempotencyKey);
        run.setSourceLeadId(sourceLeadId);
        run.setStatus(status);
        run.setReasonCode("TEST");
        run.setIdempotencyKey(idempotencyKey);
        return workflowRunRepository.saveAndFlush(run);
    }

    private WebhookEventEntity saveWebhook(String eventId, String sourceLeadId, OffsetDateTime receivedAt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", "peopleUpdated");

        WebhookEventEntity entity = new WebhookEventEntity();
        entity.setSource(WebhookSource.FUB);
        entity.setEventId(eventId);
        entity.setEventType("peopleUpdated");
        entity.setCatalogState(EventSupportState.SUPPORTED);
        entity.setNormalizedDomain(NormalizedDomain.ASSIGNMENT);
        entity.setNormalizedAction(NormalizedAction.UPDATED);
        entity.setStatus(WebhookEventStatus.RECEIVED);
        entity.setSourceLeadId(sourceLeadId);
        entity.setPayload(payload);
        entity.setPayloadHash("hash-" + eventId);
        entity.setReceivedAt(receivedAt);
        return webhookEventRepository.saveAndFlush(entity);
    }
}
