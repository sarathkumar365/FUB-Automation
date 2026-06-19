package com.flux.service.workflow.trigger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.config.WorkflowTriggerRouterProperties;
import com.flux.persistence.entity.AutomationWorkflowEntity;
import com.flux.persistence.entity.WorkflowStatus;
import com.flux.persistence.repository.AutomationWorkflowRepository;
import com.flux.service.event.DomainEvent;
import com.flux.service.event.DomainEventListener;
import com.flux.service.event.EngineEchoGate;
import com.flux.service.workflow.WorkflowExecutionManager;
import com.flux.service.workflow.WorkflowPlanRequest;
import com.flux.service.workflow.WorkflowPlanningResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes domain events to workflows. Registered as a {@link DomainEventListener},
 * so it is invoked after the emitting transaction commits. For each
 * ACTIVE workflow whose {@code trigger.on} matches the event kind, it applies
 * the engine-echo gate, evaluates the filter, and plans a run per entity.
 */
@Service
public class WorkflowTriggerRouter implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerRouter.class);

    private final AutomationWorkflowRepository workflowRepository;
    private final DomainEventTriggerType domainEventTriggerType;
    private final EngineEchoGate engineEchoGate;
    private final WorkflowExecutionManager workflowExecutionManager;
    private final WorkflowTriggerRouterProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowTriggerRouter(
            AutomationWorkflowRepository workflowRepository,
            DomainEventTriggerType domainEventTriggerType,
            EngineEchoGate engineEchoGate,
            WorkflowExecutionManager workflowExecutionManager,
            WorkflowTriggerRouterProperties properties,
            ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.domainEventTriggerType = domainEventTriggerType;
        this.engineEchoGate = engineEchoGate;
        this.workflowExecutionManager = workflowExecutionManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onEvent(DomainEvent event) {
        route(event);
    }

    public RoutingSummary route(DomainEvent event) {
        if (event == null || event.eventKind() == null) {
            return new RoutingSummary(0, 0, 0, 0, 0, 0, 0);
        }

        List<AutomationWorkflowEntity> activeWorkflows = workflowRepository.findByStatus(WorkflowStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(AutomationWorkflowEntity::getId))
                .toList();

        Map<String, Object> eventPayload = toMap(event.payload());

        int matchedWorkflows = 0;
        int skippedCount = 0;
        List<PlannedTarget> targets = new ArrayList<>();

        for (AutomationWorkflowEntity workflow : activeWorkflows) {
            Map<String, Object> trigger = workflow.getTrigger();
            if (!ParsedTrigger.from(trigger).subscribesTo(event.eventKind())) {
                skippedCount++;
                continue;
            }
            if (engineEchoGate.shouldExclude(event, trigger)) {
                skippedCount++;
                log.info("Skipped engine-caused event workflowKey={} eventKind={} eventId={} reactToEngineEvents={}",
                        workflow.getKey(), event.eventKind(), event.id(), trigger.get("reactToEngineEvents"));
                continue;
            }

            boolean matches;
            try {
                matches = domainEventTriggerType.matches(event, trigger);
            } catch (RuntimeException ex) {
                skippedCount++;
                log.warn("Domain-event trigger match failed workflowKey={} eventId={}", workflow.getKey(), event.id(), ex);
                continue;
            }
            if (!matches) {
                skippedCount++;
                continue;
            }

            matchedWorkflows++;
            for (EntityRef entity : domainEventTriggerType.extractEntities(event)) {
                targets.add(new PlannedTarget(workflow, entity));
            }
        }

        int maxFanout = Math.max(1, properties.getMaxFanoutPerEvent());
        int candidatePlanCount = targets.size();
        int cappedCount = 0;
        if (targets.size() > maxFanout) {
            cappedCount = targets.size() - maxFanout;
            targets = new ArrayList<>(targets.subList(0, maxFanout));
            log.warn("Domain-event routing capped eventId={} candidatePlanCount={} maxFanout={} cappedCount={}",
                    event.id(), candidatePlanCount, maxFanout, cappedCount);
        }

        int plannedCount = 0;
        int failedCount = 0;
        for (PlannedTarget target : targets) {
            WorkflowPlanRequest request = new WorkflowPlanRequest(
                    target.workflow().getKey(),
                    event.sourceSystem() != null ? event.sourceSystem() : "UNKNOWN",
                    event.id() != null ? String.valueOf(event.id()) : null,
                    event.sourceEventId(),
                    target.entity().entityId(),
                    eventPayload,
                    event.id());
            try {
                WorkflowPlanningResult result = workflowExecutionManager.plan(request);
                if (result.status() == WorkflowPlanningResult.PlanningStatus.FAILED) {
                    failedCount++;
                } else {
                    plannedCount++;
                }
                log.info("Domain-event planned eventId={} eventKind={} workflowKey={} entityId={} status={} runId={}",
                        event.id(), event.eventKind(), target.workflow().getKey(),
                        target.entity().entityId(), result.status(), result.runId());
            } catch (RuntimeException ex) {
                failedCount++;
                log.error("Domain-event planning failed eventId={} workflowKey={} entityId={}",
                        event.id(), target.workflow().getKey(), target.entity().entityId(), ex);
            }
        }

        return new RoutingSummary(
                activeWorkflows.size(), matchedWorkflows, candidatePlanCount,
                plannedCount, failedCount, skippedCount, cappedCount);
    }

    private Map<String, Object> toMap(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(payload, new TypeReference<>() {
            });
            return map != null ? map : Map.of();
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to convert event payload to map, using empty payload", ex);
            return Map.of();
        }
    }

    private record PlannedTarget(
            AutomationWorkflowEntity workflow,
            EntityRef entity) {
    }

    public record RoutingSummary(
            int activeWorkflowCount,
            int matchedWorkflowCount,
            int candidatePlanCount,
            int plannedCount,
            int failedCount,
            int skippedCount,
            int cappedCount) {
    }
}
