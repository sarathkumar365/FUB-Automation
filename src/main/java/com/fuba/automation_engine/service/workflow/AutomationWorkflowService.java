package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.service.support.KeyNormalizationHelper;
import com.fuba.automation_engine.service.workflow.trigger.WorkflowTriggerRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutomationWorkflowService {

    private final AutomationWorkflowRepository workflowRepository;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowStepRegistry stepRegistry;
    private final WorkflowTriggerRegistry triggerRegistry;

    public AutomationWorkflowService(
            AutomationWorkflowRepository workflowRepository,
            WorkflowGraphValidator graphValidator,
            WorkflowStepRegistry stepRegistry,
            WorkflowTriggerRegistry triggerRegistry) {
        this.workflowRepository = workflowRepository;
        this.graphValidator = graphValidator;
        this.stepRegistry = stepRegistry;
        this.triggerRegistry = triggerRegistry;
    }

    public CreateResult create(
            String key,
            String name,
            String description,
            Map<String, Object> trigger,
            Map<String, Object> graph,
            String statusStr) {
        String normalizedWorkflowKey = normalizeWorkflowKey(key);
        if (normalizedWorkflowKey == null || name == null || name.isBlank()) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, "key and name are required");
        }
        if (graph == null || graph.isEmpty()) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, "graph is required");
        }
        String triggerValidationError = validateTrigger(trigger);
        if (triggerValidationError != null) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, triggerValidationError);
        }
        if (workflowRepository.findMaxVersionNumberByKey(normalizedWorkflowKey).isPresent()) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, "workflow key already exists");
        }

        GraphValidationResult validation = graphValidator.validate(graph);
        if (!validation.valid()) {
            return new CreateResult(CreateStatus.INVALID_GRAPH, null,
                    String.join("; ", validation.errors()));
        }

        WorkflowStatus status = parseStatus(statusStr);

        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(normalizedWorkflowKey);
        entity.setName(name);
        entity.setDescription(description);
        entity.setTrigger(trigger);
        entity.setGraph(graph);
        entity.setStatus(status);
        entity.setVersionNumber(1);

        AutomationWorkflowEntity saved = workflowRepository.saveAndFlush(entity);
        return new CreateResult(CreateStatus.SUCCESS, saved, null);
    }

    @Transactional(readOnly = true)
    public Optional<AutomationWorkflowEntity> getById(Long id) {
        return workflowRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<AutomationWorkflowEntity> getActiveByKey(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return Optional.empty();
        }
        return workflowRepository.findFirstByKeyAndStatusOrderByIdDesc(workflowKey, WorkflowStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Optional<AutomationWorkflowEntity> getLatestByKey(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return Optional.empty();
        }
        return workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
    }

    @Transactional(readOnly = true)
    public List<AutomationWorkflowEntity> listVersions(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return List.of();
        }
        return workflowRepository.findByKeyOrderByVersionNumberDesc(workflowKey);
    }

    public UpdateResult update(
            String key,
            String name,
            String description,
            Map<String, Object> trigger,
            Map<String, Object> graph) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null || name == null || name.isBlank()) {
            return new UpdateResult(UpdateStatus.INVALID_INPUT, null, "key and name are required");
        }
        if (graph == null || graph.isEmpty()) {
            return new UpdateResult(UpdateStatus.INVALID_INPUT, null, "graph is required");
        }

        Optional<AutomationWorkflowEntity> latestOpt = workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
        if (latestOpt.isEmpty()) {
            return new UpdateResult(UpdateStatus.NOT_FOUND, null, "Workflow not found");
        }

        GraphValidationResult validation = graphValidator.validate(graph);
        if (!validation.valid()) {
            return new UpdateResult(UpdateStatus.INVALID_GRAPH, null, String.join("; ", validation.errors()));
        }

        int nextVersion = workflowRepository.findMaxVersionNumberByKey(workflowKey).orElse(0) + 1;
        AutomationWorkflowEntity latest = latestOpt.get();
        Map<String, Object> nextTrigger = latest.getTrigger();
        if (trigger != null) {
            String triggerValidationError = validateTrigger(trigger);
            if (triggerValidationError != null) {
                return new UpdateResult(UpdateStatus.INVALID_INPUT, null, triggerValidationError);
            }
            nextTrigger = trigger;
        }

        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(workflowKey);
        entity.setName(name);
        entity.setDescription(description);
        entity.setTrigger(nextTrigger);
        entity.setGraph(graph);
        entity.setStatus(WorkflowStatus.INACTIVE);
        entity.setVersionNumber(nextVersion);

        AutomationWorkflowEntity saved = workflowRepository.saveAndFlush(entity);
        return new UpdateResult(UpdateStatus.SUCCESS, saved, null);
    }

    public UpdateResult rollback(String key, Integer toVersionNumber) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null || toVersionNumber == null || toVersionNumber <= 0) {
            return new UpdateResult(UpdateStatus.INVALID_INPUT, null, "key and toVersion are required");
        }

        Optional<AutomationWorkflowEntity> latestOpt = workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
        if (latestOpt.isEmpty()) {
            return new UpdateResult(UpdateStatus.NOT_FOUND, null, "Workflow not found");
        }

        Optional<AutomationWorkflowEntity> targetOpt = workflowRepository.findByKeyAndVersionNumber(workflowKey, toVersionNumber);
        if (targetOpt.isEmpty()) {
            return new UpdateResult(UpdateStatus.NOT_FOUND, null, "Rollback target version not found");
        }

        int nextVersion = workflowRepository.findMaxVersionNumberByKey(workflowKey).orElse(0) + 1;
        AutomationWorkflowEntity target = targetOpt.get();

        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(workflowKey);
        entity.setName(target.getName());
        entity.setDescription(target.getDescription());
        entity.setTrigger(target.getTrigger());
        entity.setGraph(target.getGraph());
        entity.setStatus(WorkflowStatus.INACTIVE);
        entity.setVersionNumber(nextVersion);

        AutomationWorkflowEntity saved = workflowRepository.saveAndFlush(entity);
        return new UpdateResult(UpdateStatus.SUCCESS, saved, null);
    }

    public ActivateResult activate(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return new ActivateResult(ActivateStatus.INVALID_INPUT, null, "key is required");
        }
        Optional<AutomationWorkflowEntity> latestOpt =
                workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
        if (latestOpt.isEmpty()) {
            return new ActivateResult(ActivateStatus.NOT_FOUND, null, "Workflow not found");
        }
        AutomationWorkflowEntity latest = latestOpt.get();
        workflowRepository.deactivateActiveWorkflowsByKeyExcludingId(
                workflowKey,
                latest.getId(),
                WorkflowStatus.ACTIVE,
                WorkflowStatus.INACTIVE);
        latest.setStatus(WorkflowStatus.ACTIVE);
        return new ActivateResult(ActivateStatus.SUCCESS, workflowRepository.saveAndFlush(latest), null);
    }

    public DeactivateResult deactivate(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return new DeactivateResult(DeactivateStatus.INVALID_INPUT, null, "key is required");
        }
        Optional<AutomationWorkflowEntity> latestOpt =
                workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
        if (latestOpt.isEmpty()) {
            return new DeactivateResult(DeactivateStatus.NOT_FOUND, null, "Workflow not found");
        }
        workflowRepository.deactivateActiveWorkflowsByKey(
                workflowKey,
                WorkflowStatus.ACTIVE,
                WorkflowStatus.INACTIVE);
        AutomationWorkflowEntity refreshedLatest = workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey)
                .orElse(latestOpt.get());
        return new DeactivateResult(DeactivateStatus.SUCCESS, refreshedLatest, null);
    }

    public ArchiveResult archive(String key) {
        String workflowKey = normalizeWorkflowKey(key);
        if (workflowKey == null) {
            return new ArchiveResult(ArchiveStatus.INVALID_INPUT, null, "key is required");
        }
        Optional<AutomationWorkflowEntity> latestOpt =
                workflowRepository.findFirstByKeyOrderByVersionNumberDesc(workflowKey);
        if (latestOpt.isEmpty()) {
            return new ArchiveResult(ArchiveStatus.NOT_FOUND, null, "Workflow not found");
        }
        AutomationWorkflowEntity latest = latestOpt.get();
        workflowRepository.deactivateActiveWorkflowsByKey(
                workflowKey,
                WorkflowStatus.ACTIVE,
                WorkflowStatus.INACTIVE);
        AutomationWorkflowEntity latestRefreshed = workflowRepository.findById(latest.getId()).orElse(latest);
        latestRefreshed.setStatus(WorkflowStatus.ARCHIVED);
        return new ArchiveResult(ArchiveStatus.SUCCESS, workflowRepository.saveAndFlush(latestRefreshed), null);
    }

    @Transactional(readOnly = true)
    public ListResult list(String status, int page, int size) {
        ParsedStatusFilter parsed = parseStatusFilter(status);
        if (!parsed.valid()) {
            return new ListResult(ListStatus.INVALID_INPUT, null, "Invalid status filter");
        }

        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, size);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize, Sort.by("key").ascending());
        Page<AutomationWorkflowEntity> latestPage =
                workflowRepository.findLatestByStatusFilter(parsed.status(), WorkflowStatus.ARCHIVED, pageable);

        PageResult<AutomationWorkflowEntity> result = new PageResult<>(
                latestPage.getContent(),
                normalizedPage,
                normalizedSize,
                latestPage.getTotalElements());
        return new ListResult(ListStatus.SUCCESS, result, null);
    }

    @Transactional(readOnly = true)
    public ValidateResult validate(Map<String, Object> graph, Map<String, Object> trigger) {
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        GraphValidationResult graphValidation = graphValidator.validate(graph);
        errors.addAll(graphValidation.errors());

        if (trigger == null || trigger.isEmpty()) {
            errors.add("trigger is required");
        } else {
            Object triggerTypeObj = trigger.get("type");
            String triggerType = triggerTypeObj instanceof String s ? s.trim() : null;
            if (triggerType == null || triggerType.isEmpty()) {
                errors.add("trigger.type is required");
            } else if (triggerRegistry.get(triggerType).isEmpty()) {
                errors.add("Unknown trigger type: " + triggerType);
            }
        }

        Object nodesObj = graph != null ? graph.get("nodes") : null;
        if (nodesObj instanceof List<?> nodes) {
            for (Object item : nodes) {
                if (!(item instanceof Map<?, ?> node)) {
                    continue;
                }

                Object stepTypeObj = node.get("type");
                if (!(stepTypeObj instanceof String stepType) || stepType.isBlank()) {
                    continue;
                }

                if (stepRegistry.get(stepType).isEmpty()) {
                    Object nodeIdObj = node.get("id");
                    String nodeId = nodeIdObj instanceof String s ? s : "unknown";
                    errors.add("Node '" + nodeId + "' references unknown step type: " + stepType);
                }
            }
        }

        return new ValidateResult(errors.isEmpty(), new ArrayList<>(errors));
    }

    private WorkflowStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return WorkflowStatus.DRAFT;
        }
        try {
            return WorkflowStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return WorkflowStatus.DRAFT;
        }
    }

    private ParsedStatusFilter parseStatusFilter(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return new ParsedStatusFilter(true, null);
        }
        try {
            WorkflowStatus parsed = WorkflowStatus.valueOf(statusStr.trim().toUpperCase());
            return new ParsedStatusFilter(true, parsed);
        } catch (IllegalArgumentException ex) {
            return new ParsedStatusFilter(false, null);
        }
    }

    private String normalizeWorkflowKey(String key) {
        return KeyNormalizationHelper.normalizeWorkflowKey(key);
    }

    private String validateTrigger(Map<String, Object> trigger) {
        if (trigger == null) {
            return null;
        }
        Object triggerTypeObj = trigger.get("type");
        String triggerType = triggerTypeObj instanceof String s ? s.trim() : null;
        if (triggerType == null || triggerType.isEmpty()) {
            return "trigger.type is required";
        }
        if (triggerRegistry.get(triggerType).isEmpty()) {
            return "Unknown trigger type: " + triggerType;
        }
        return null;
    }

    private record ParsedStatusFilter(boolean valid, WorkflowStatus status) {
    }

    public record CreateResult(CreateStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public record UpdateResult(UpdateStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public record ActivateResult(ActivateStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public record DeactivateResult(DeactivateStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public record ArchiveResult(ArchiveStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public record ListResult(ListStatus status, PageResult<AutomationWorkflowEntity> page, String errorMessage) {
    }

    public record ValidateResult(boolean valid, List<String> errors) {
    }

    public record PageResult<T>(List<T> items, int page, int size, long total) {
    }

    public enum CreateStatus {
        SUCCESS,
        INVALID_INPUT,
        INVALID_GRAPH
    }

    public enum UpdateStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_INPUT,
        INVALID_GRAPH
    }

    public enum ActivateStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_INPUT
    }

    public enum DeactivateStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_INPUT
    }

    public enum ArchiveStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_INPUT
    }

    public enum ListStatus {
        SUCCESS,
        INVALID_INPUT
    }
}
