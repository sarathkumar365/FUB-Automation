package com.fuba.automation_engine.service.workflow.trigger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.config.WorkflowTriggerRouterProperties;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.workflow.WorkflowExecutionManager;
import com.fuba.automation_engine.service.workflow.WorkflowPlanRequest;
import com.fuba.automation_engine.service.workflow.WorkflowPlanningResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTriggerRouter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerRouter.class);

    private final AutomationWorkflowRepository workflowRepository;
    private final WorkflowTriggerRegistry triggerRegistry;
    private final WorkflowExecutionManager workflowExecutionManager;
    private final WorkflowTriggerRouterProperties properties;
    private final ObjectMapper objectMapper;

    public WorkflowTriggerRouter(
            AutomationWorkflowRepository workflowRepository,
            WorkflowTriggerRegistry triggerRegistry,
            WorkflowExecutionManager workflowExecutionManager,
            WorkflowTriggerRouterProperties properties,
            ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.triggerRegistry = triggerRegistry;
        this.workflowExecutionManager = workflowExecutionManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public RoutingSummary route(NormalizedWebhookEvent event) {
        if (event == null) {
            return new RoutingSummary(0, 0, 0, 0, 0, 0, 0);
        }

        List<AutomationWorkflowEntity> activeWorkflows = workflowRepository.findByStatus(WorkflowStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(AutomationWorkflowEntity::getId))
                .toList();

        Map<String, Object> payloadMap = toMap(event.payload());
        String eventType = resolveEventType(event, payloadMap);

        int matchedWorkflows = 0;
        int skippedCount = 0;
        List<PlannedTarget> targets = new ArrayList<>();

        for (AutomationWorkflowEntity workflow : activeWorkflows) {
            Map<String, Object> trigger = workflow.getTrigger();
            if (trigger == null || trigger.isEmpty()) {
                skippedCount++;
                continue;
            }

            String triggerTypeId = toStringOrNull(trigger.get("type"));
            if (triggerTypeId == null || triggerTypeId.isBlank()) {
                skippedCount++;
                continue;
            }

            WorkflowTriggerType triggerType = triggerRegistry.get(triggerTypeId.trim()).orElse(null);
            if (triggerType == null) {
                skippedCount++;
                log.warn("Workflow trigger type unknown; skipping workflowId={} triggerType={}", workflow.getId(), triggerTypeId);
                continue;
            }

            Map<String, Object> triggerConfig = toConfigMap(trigger.get("config"));
            TriggerMatchContext context = new TriggerMatchContext(
                    event.sourceSystem(),
                    eventType,
                    event.normalizedDomain(),
                    event.normalizedAction(),
                    payloadMap,
                    triggerConfig);

            boolean matches;
            try {
                matches = triggerType.matches(context);
            } catch (RuntimeException ex) {
                skippedCount++;
                log.warn(
                        "Workflow trigger match evaluation failed; skipping workflowId={} triggerType={} eventId={}",
                        workflow.getId(),
                        triggerTypeId,
                        event.eventId(),
                        ex);
                continue;
            }

            if (!matches) {
                skippedCount++;
                continue;
            }

            matchedWorkflows++;

            List<EntityRef> entities;
            try {
                entities = triggerType.extractEntities(context);
            } catch (RuntimeException ex) {
                skippedCount++;
                log.warn(
                        "Workflow trigger entity extraction failed; skipping workflowId={} triggerType={} eventId={}",
                        workflow.getId(),
                        triggerTypeId,
                        event.eventId(),
                        ex);
                continue;
            }

            for (EntityRef entity : entities) {
                targets.add(new PlannedTarget(workflow, entity));
            }
        }

        int maxFanout = Math.max(1, properties.getMaxFanoutPerEvent());
        int candidatePlanCount = targets.size();
        int cappedCount = 0;
        if (targets.size() > maxFanout) {
            cappedCount = targets.size() - maxFanout;
            targets = new ArrayList<>(targets.subList(0, maxFanout));
            log.warn(
                    "Workflow trigger routing capped eventId={} source={} candidatePlanCount={} maxFanout={} cappedCount={}",
                    event.eventId(),
                    event.sourceSystem(),
                    candidatePlanCount,
                    maxFanout,
                    cappedCount);
        }

        int plannedCount = 0;
        int failedCount = 0;
        for (PlannedTarget target : targets) {
            WorkflowPlanRequest request = new WorkflowPlanRequest(
                    target.workflow().getKey(),
                    event.sourceSystem() != null ? event.sourceSystem().name() : "UNKNOWN",
                    event.eventId(),
                    null,
                    target.entity().entityId(),
                    payloadMap);
            try {
                WorkflowPlanningResult result = workflowExecutionManager.plan(request);
                if (result.status() == WorkflowPlanningResult.PlanningStatus.FAILED) {
                    failedCount++;
                } else {
                    plannedCount++;
                }
                log.info(
                        "Workflow trigger planned eventId={} workflowKey={} workflowId={} entityId={} planningStatus={} runId={} reasonCode={}",
                        event.eventId(),
                        target.workflow().getKey(),
                        target.workflow().getId(),
                        target.entity().entityId(),
                        result.status(),
                        result.runId(),
                        result.reasonCode());
            } catch (RuntimeException ex) {
                failedCount++;
                log.error(
                        "Workflow trigger planning failed eventId={} workflowKey={} workflowId={} entityId={}",
                        event.eventId(),
                        target.workflow().getKey(),
                        target.workflow().getId(),
                        target.entity().entityId(),
                        ex);
            }
        }

        return new RoutingSummary(
                activeWorkflows.size(),
                matchedWorkflows,
                candidatePlanCount,
                plannedCount,
                failedCount,
                skippedCount,
                cappedCount);
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
            log.warn("Unable to convert webhook payload to map, using empty payload", ex);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toConfigMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String toStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String resolveEventType(NormalizedWebhookEvent event, Map<String, Object> payloadMap) {
        if (event.sourceEventType() != null && !event.sourceEventType().isBlank()) {
            return event.sourceEventType().trim();
        }
        Object payloadEventType = payloadMap.get("eventType");
        if (payloadEventType == null) {
            return "";
        }
        return String.valueOf(payloadEventType).trim();
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
