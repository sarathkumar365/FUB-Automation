package com.flux.service.workflow.trigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flux.config.WorkflowTriggerRouterProperties;
import com.flux.persistence.entity.AutomationWorkflowEntity;
import com.flux.persistence.entity.WorkflowStatus;
import com.flux.persistence.repository.AutomationWorkflowRepository;
import com.flux.service.event.DomainEvent;
import com.flux.service.event.EngineEchoGate;
import com.flux.service.workflow.WorkflowExecutionManager;
import com.flux.service.workflow.WorkflowPlanRequest;
import com.flux.service.workflow.WorkflowPlanningResult;
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
    private static final String KIND = "person.state_changed";

    @Test
    void shouldApplyDeterministicFanoutCap() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        DomainEventTriggerType triggerType = mock(DomainEventTriggerType.class);

        when(triggerType.matches(any(DomainEvent.class), any())).thenReturn(true);
        when(triggerType.extractEntities(any(DomainEvent.class)))
                .thenReturn(List.of(
                        new EntityRef("person", "1"),
                        new EntityRef("person", "2"),
                        new EntityRef("person", "3"),
                        new EntityRef("person", "4")));
        when(executionManager.plan(any(WorkflowPlanRequest.class)))
                .thenReturn(new WorkflowPlanningResult(WorkflowPlanningResult.PlanningStatus.PLANNED, 1L, null));

        AutomationWorkflowEntity later = workflow(20L, "WF_LATER", Map.of("on", KIND));
        AutomationWorkflowEntity earlier = workflow(10L, "WF_EARLY", Map.of("on", KIND));
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(later, earlier));

        WorkflowTriggerRouterProperties properties = new WorkflowTriggerRouterProperties();
        properties.setMaxFanoutPerEvent(3);

        WorkflowTriggerRouter router = router(repository, triggerType, new EngineEchoGate(false), executionManager, properties);

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(KIND, false));

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
        assertEquals("1", requests.get(0).sourcePersonId());
        assertEquals("WF_EARLY", requests.get(1).workflowKey());
        assertEquals("2", requests.get(1).sourcePersonId());
        assertEquals("WF_EARLY", requests.get(2).workflowKey());
        assertEquals("3", requests.get(2).sourcePersonId());
    }

    @Test
    void shouldSkipTriggersWithoutMatchingOn() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        DomainEventTriggerType triggerType = mock(DomainEventTriggerType.class);

        AutomationWorkflowEntity nullTrigger = workflow(1L, "WF_NULL", null);
        AutomationWorkflowEntity otherKind = workflow(2L, "WF_OTHER", Map.of("on", "person.created"));
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(nullTrigger, otherKind));

        WorkflowTriggerRouter router = router(repository, triggerType, new EngineEchoGate(false),
                executionManager, new WorkflowTriggerRouterProperties());

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(KIND, false));

        assertEquals(2, summary.activeWorkflowCount());
        assertEquals(0, summary.matchedWorkflowCount());
        assertEquals(0, summary.candidatePlanCount());
        assertEquals(2, summary.skippedCount());
        verify(executionManager, times(0)).plan(any(WorkflowPlanRequest.class));
    }

    @Test
    void shouldSkipEngineEchoWhenGateClosed() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        DomainEventTriggerType triggerType = mock(DomainEventTriggerType.class);
        when(triggerType.matches(any(DomainEvent.class), any())).thenReturn(true);
        when(triggerType.extractEntities(any(DomainEvent.class))).thenReturn(List.of(new EntityRef("person", "1")));

        AutomationWorkflowEntity wf = workflow(1L, "WF", Map.of("on", KIND));
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(wf));

        WorkflowTriggerRouter router = router(repository, triggerType, new EngineEchoGate(false),
                executionManager, new WorkflowTriggerRouterProperties());

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(KIND, true)); // engine-caused, gate closed

        assertEquals(0, summary.matchedWorkflowCount());
        assertEquals(1, summary.skippedCount());
        verify(executionManager, times(0)).plan(any(WorkflowPlanRequest.class));
    }

    @Test
    void shouldRouteAnyOfTriggerSubscribedToEventKind() {
        AutomationWorkflowRepository repository = mock(AutomationWorkflowRepository.class);
        WorkflowExecutionManager executionManager = mock(WorkflowExecutionManager.class);
        DomainEventTriggerType triggerType = mock(DomainEventTriggerType.class);
        when(triggerType.matches(any(DomainEvent.class), any())).thenReturn(true);
        when(triggerType.extractEntities(any(DomainEvent.class))).thenReturn(List.of(new EntityRef("person", "1")));
        when(executionManager.plan(any(WorkflowPlanRequest.class)))
                .thenReturn(new WorkflowPlanningResult(WorkflowPlanningResult.PlanningStatus.PLANNED, 1L, null));

        AutomationWorkflowEntity anyOfWf = workflow(1L, "WF_ANYOF", Map.of("anyOf", List.of(
                Map.of("on", "person.created"),
                Map.of("on", KIND))));
        when(repository.findByStatus(WorkflowStatus.ACTIVE)).thenReturn(List.of(anyOfWf));

        WorkflowTriggerRouter router = router(repository, triggerType, new EngineEchoGate(false),
                executionManager, new WorkflowTriggerRouterProperties());

        WorkflowTriggerRouter.RoutingSummary summary = router.route(event(KIND, false));

        assertEquals(1, summary.matchedWorkflowCount());
        assertEquals(1, summary.plannedCount());
    }

    private WorkflowTriggerRouter router(
            AutomationWorkflowRepository repository,
            DomainEventTriggerType triggerType,
            EngineEchoGate gate,
            WorkflowExecutionManager executionManager,
            WorkflowTriggerRouterProperties properties) {
        return new WorkflowTriggerRouter(repository, triggerType, gate, executionManager, properties, OBJECT_MAPPER);
    }

    private AutomationWorkflowEntity workflow(long id, String key, Map<String, Object> trigger) {
        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setId(id);
        entity.setKey(key);
        entity.setStatus(WorkflowStatus.ACTIVE);
        if (trigger != null) {
            entity.setTrigger(trigger);
        }
        return entity;
    }

    private DomainEvent event(String kind, boolean engineCaused) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.set("changed_fields", OBJECT_MAPPER.valueToTree(List.of("assignedUserId")));
        if (engineCaused) {
            payload.put("source", "ENGINE");
        }
        return new DomainEvent(1L, kind, "FUB", 7L, "person", "1", payload);
    }
}
