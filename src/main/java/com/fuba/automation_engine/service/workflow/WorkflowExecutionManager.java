package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionManager.class);
    private static final String IDEMPOTENCY_KEY_PREFIX = "WEM1|";
    private static final String IDEMPOTENCY_CONSTRAINT = "uk_workflow_runs_idempotency_key";

    private final AutomationWorkflowRepository workflowRepository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowRunStepRepository stepRepository;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowStepRegistry stepRegistry;
    private final EntityManager entityManager;
    private final Clock clock;

    public WorkflowExecutionManager(
            AutomationWorkflowRepository workflowRepository,
            WorkflowRunRepository runRepository,
            WorkflowRunStepRepository stepRepository,
            WorkflowGraphValidator graphValidator,
            WorkflowStepRegistry stepRegistry,
            EntityManager entityManager,
            Clock clock) {
        this.workflowRepository = workflowRepository;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.graphValidator = graphValidator;
        this.stepRegistry = stepRegistry;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public WorkflowPlanningResult plan(WorkflowPlanRequest request) {
        if (request == null) {
            return new WorkflowPlanningResult(WorkflowPlanningResult.PlanningStatus.FAILED, null, "NULL_REQUEST");
        }

        String idempotencyKey = buildIdempotencyKey(request);

        Optional<WorkflowRunEntity> existingByKey = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return new WorkflowPlanningResult(
                    WorkflowPlanningResult.PlanningStatus.DUPLICATE_IGNORED,
                    existingByKey.get().getId(),
                    "DUPLICATE_IDEMPOTENCY_KEY");
        }

        Optional<AutomationWorkflowEntity> workflowOpt =
                workflowRepository.findFirstByKeyAndStatusOrderByIdDesc(request.workflowKey(), WorkflowStatus.ACTIVE);
        if (workflowOpt.isEmpty()) {
            return new WorkflowPlanningResult(
                    WorkflowPlanningResult.PlanningStatus.BLOCKED, null, "WORKFLOW_NOT_FOUND");
        }

        AutomationWorkflowEntity workflow = workflowOpt.get();
        Map<String, Object> graph = workflow.getGraph();

        GraphValidationResult validation = graphValidator.validate(graph);
        if (!validation.valid()) {
            log.warn("Workflow graph validation failed workflowKey={} errors={}", request.workflowKey(), validation.errors());
            return new WorkflowPlanningResult(
                    WorkflowPlanningResult.PlanningStatus.FAILED, null, "GRAPH_INVALID");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowId(workflow.getId());
        run.setWorkflowKey(workflow.getKey());
        run.setWorkflowVersion(workflow.getVersion() == null ? 0L : workflow.getVersion());
        run.setWorkflowGraphSnapshot(graph);
        run.setTriggerPayload(request.triggerPayload());
        run.setSource(request.source() != null ? request.source() : "UNKNOWN");
        run.setEventId(request.eventId());
        run.setWebhookEventId(request.webhookEventId());
        run.setSourceLeadId(request.sourceLeadId());
        run.setStatus(WorkflowRunStatus.PENDING);
        run.setIdempotencyKey(idempotencyKey);

        try {
            WorkflowRunEntity savedRun = runRepository.saveAndFlush(run);
            materializeSteps(savedRun.getId(), graph, now);
            return new WorkflowPlanningResult(
                    WorkflowPlanningResult.PlanningStatus.PLANNED, savedRun.getId(), null);
        } catch (DataIntegrityViolationException ex) {
            return handlePotentialDuplicate(request, idempotencyKey, ex);
        }
    }

    private void materializeSteps(Long runId, Map<String, Object> graph, OffsetDateTime now) {
        String entryNode = (String) graph.get("entryNode");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");

        // Build reverse adjacency: for each node, which nodes can transition INTO it
        Map<String, Set<String>> predecessors = buildPredecessorMap(nodes);

        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            String type = (String) node.get("type");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = node.get("config") instanceof Map<?, ?>
                    ? (Map<String, Object>) node.get("config")
                    : Map.of();

            WorkflowRunStepEntity step = new WorkflowRunStepEntity();
            step.setRunId(runId);
            step.setNodeId(nodeId);
            step.setStepType(type);
            step.setConfigSnapshot(config);

            Set<String> preds = predecessors.getOrDefault(nodeId, Set.of());

            if (nodeId.equals(entryNode)) {
                step.setStatus(WorkflowRunStepStatus.PENDING);
                step.setPendingDependencyCount(0);
                step.setDueAt(resolveEntryDueAt(config, now));
            } else {
                step.setStatus(WorkflowRunStepStatus.WAITING_DEPENDENCY);
                step.setPendingDependencyCount(preds.size());
                step.setDependsOnNodeIds(preds.isEmpty() ? null : new ArrayList<>(preds));
                step.setDueAt(null);
            }

            stepRepository.save(step);
        }
        stepRepository.flush();
    }

    private Map<String, Set<String>> buildPredecessorMap(List<Map<String, Object>> nodes) {
        Map<String, Set<String>> predecessors = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            Object transitionsObj = node.get("transitions");
            if (!(transitionsObj instanceof Map<?, ?> transitions)) {
                continue;
            }
            for (Object value : transitions.values()) {
                if (value instanceof List<?> nextNodes) {
                    for (Object target : nextNodes) {
                        String targetId = String.valueOf(target);
                        predecessors.computeIfAbsent(targetId, k -> new HashSet<>()).add(nodeId);
                    }
                }
            }
        }
        return predecessors;
    }

    private OffsetDateTime resolveEntryDueAt(Map<String, Object> config, OffsetDateTime now) {
        Object delayObj = config.get("delayMinutes");
        if (delayObj instanceof Number number) {
            int delayMinutes = Math.max(0, number.intValue());
            return now.plusMinutes(delayMinutes);
        }
        return now;
    }

    private WorkflowPlanningResult handlePotentialDuplicate(
            WorkflowPlanRequest request,
            String idempotencyKey,
            DataIntegrityViolationException ex) {
        if (!isIdempotencyConflict(ex)) {
            throw ex;
        }

        entityManager.clear();
        Optional<WorkflowRunEntity> existingRun = runRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRun.isEmpty()) {
            throw new IllegalStateException("Idempotency conflict detected but no existing run found", ex);
        }

        log.info(
                "Duplicate workflow execution planning ignored idempotencyKey={} eventId={} workflowKey={}",
                idempotencyKey,
                request.eventId(),
                request.workflowKey());
        return new WorkflowPlanningResult(
                WorkflowPlanningResult.PlanningStatus.DUPLICATE_IGNORED,
                existingRun.get().getId(),
                "DUPLICATE_IDEMPOTENCY_KEY");
    }

    private String buildIdempotencyKey(WorkflowPlanRequest request) {
        StringJoiner joiner = new StringJoiner("|");
        joiner.add(normalize(request.workflowKey()));
        joiner.add(normalize(request.source()));
        joiner.add(normalize(request.sourceLeadId()));
        if (hasText(request.eventId())) {
            joiner.add("EVENT");
            joiner.add(request.eventId().trim());
        } else {
            joiner.add("FALLBACK");
            joiner.add("NO_EVENT");
        }
        return IDEMPOTENCY_KEY_PREFIX + sha256Hex(joiner.toString());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private boolean isIdempotencyConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalize(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
