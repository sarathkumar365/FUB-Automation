package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.WorkflowTriggerRouterProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.workflow.WorkflowExecutionManager;
import com.fuba.automation_engine.service.workflow.WorkflowPlanRequest;
import com.fuba.automation_engine.service.workflow.WorkflowPlanningResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTriggerRouterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldApplyDeterministicFanoutCap() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        WorkflowTriggerType triggerType = mock(WorkflowTriggerType.class);

        when(triggerType.id()).thenReturn("test_trigger");
        when(triggerType.matches(any(TriggerMatchContext.class))).thenReturn(true);
        when(triggerType.extractEntities(any(TriggerMatchContext.class)))
                .thenReturn(List.of(
                        new EntityRef("lead", "1"),
                        new EntityRef("lead", "2"),
                        new EntityRef("lead", "3"),
                        new EntityRef("lead", "4")));
        when(executionManager.plan(any(WorkflowPlanRequest.class)))
                .thenReturn(new WorkflowPlanningResult(WorkflowPlanningResult.PlanningStatus.PLANNED, 1L, null));

        AutomationWorkflowEntity laterId = workflow(20L, "WF_LATER", WorkflowStatus.ACTIVE, "test_trigger", Map.of());
        AutomationWorkflowEntity earlierId = workflow(10L, "WF_EARLY", WorkflowStatus.ACTIVE, "test_trigger", Map.of());
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(laterId, earlierId));

        WorkflowTriggerRouterProperties properties = new WorkflowTriggerRouterProperties();
        properties.setMaxFanoutPerEvent(3);

        WorkflowTriggerRouter router = new WorkflowTriggerRouter(
                repository,
                new WorkflowTriggerRegistry(List.of(triggerType)),
                executionManager,
                properties,
                OBJECT_MAPPER);

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payloadWithIds(100, 200)));

        assertEquals(2, summary.activeWorkflowCount());
        assertEquals(2, summary.matchedWorkflowCount());
        assertEquals(8, summary.candidatePlanCount());
        assertEquals(3, summary.plannedCount());
        assertEquals(0, summary.failedCount());
        assertEquals(5, summary.cappedCount());

        ArgumentCaptor<WorkflowPlanRequest> requestCaptor = ArgumentCaptor.forClass(WorkflowPlanRequest.class);
        verify(executionManager, times(3)).plan(requestCaptor.capture());
        List<WorkflowPlanRequest> requests = requestCaptor.getAllValues();

        assertEquals("WF_EARLY", requests.get(0).workflowKey());
        assertEquals("1", requests.get(0).sourceLeadId());
        assertEquals("WF_EARLY", requests.get(1).workflowKey());
        assertEquals("2", requests.get(1).sourceLeadId());
        assertEquals("WF_EARLY", requests.get(2).workflowKey());
        assertEquals("3", requests.get(2).sourceLeadId());
    }

    @Test
    void shouldSkipUnknownAndNullTriggers() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        WorkflowTriggerType triggerType = mock(WorkflowTriggerType.class);

        when(triggerType.id()).thenReturn("known");

        AutomationWorkflowEntity nullTrigger = workflow(1L, "WF_NULL", WorkflowStatus.ACTIVE, null, null);
        nullTrigger.setTrigger(null);
        AutomationWorkflowEntity unknownTrigger = workflow(2L, "WF_UNKNOWN", WorkflowStatus.ACTIVE, "unknown", Map.of());
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(nullTrigger, unknownTrigger));

        WorkflowTriggerRouter router = new WorkflowTriggerRouter(
                repository,
                new WorkflowTriggerRegistry(List.of(triggerType)),
                executionManager,
                new WorkflowTriggerRouterProperties(),
                OBJECT_MAPPER);

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(payloadWithIds(10)));

        assertEquals(2, summary.activeWorkflowCount());
        assertEquals(0, summary.matchedWorkflowCount());
        assertEquals(0, summary.candidatePlanCount());
        assertEquals(2, summary.skippedCount());
        verify(executionManager, times(0)).plan(any(WorkflowPlanRequest.class));
    }

    private AutomationWorkflowEntity workflow(
            long id,
            String key,
            WorkflowStatus status,
            String triggerType,
            Map<String, Object> triggerConfig) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setId(id);
        entity.setKey(key);
        entity.setStatus(status);
        if (triggerType != null) {
            entity.setTrigger(Map.of("type", triggerType, "config", triggerConfig != null ? triggerConfig : Map.of()));
        }
        return entity;
    }

    private NormalizedWebhookEvent event(ObjectNode payload) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-router-unit",
                "peopleUpdated",
                null,
                null,
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash-router-unit");
    }

    private ObjectNode payloadWithIds(long... ids) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "peopleUpdated");
        var array = payload.putArray("resourceIds");
        for (long id : ids) {
            array.add(id);
        }
        return payload;
    }
}
