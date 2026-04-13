package com.fuba.automation_engine.service.workflow;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AutomationWorkflowService {

    private final AutomationWorkflowRepository workflowRepository;
    private final WorkflowGraphValidator graphValidator;

    public AutomationWorkflowService(
            AutomationWorkflowRepository workflowRepository,
            WorkflowGraphValidator graphValidator) {
        this.workflowRepository = workflowRepository;
        this.graphValidator = graphValidator;
    }

    public CreateResult create(String key, String name, String description, Map<String, Object> graph, String statusStr) {
        if (key == null || key.isBlank() || name == null || name.isBlank()) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, "key and name are required");
        }
        if (graph == null || graph.isEmpty()) {
            return new CreateResult(CreateStatus.INVALID_INPUT, null, "graph is required");
        }

        GraphValidationResult validation = graphValidator.validate(graph);
        if (!validation.valid()) {
            return new CreateResult(CreateStatus.INVALID_GRAPH, null,
                    String.join("; ", validation.errors()));
        }

        WorkflowStatus status = parseStatus(statusStr);

        AutomationWorkflowEntity entity = new AutomationWorkflowEntity();
        entity.setKey(key);
        entity.setName(name);
        entity.setDescription(description);
        entity.setGraph(graph);
        entity.setStatus(status);

        AutomationWorkflowEntity saved = workflowRepository.saveAndFlush(entity);
        return new CreateResult(CreateStatus.SUCCESS, saved, null);
    }

    @Transactional(readOnly = true)
    public Optional<AutomationWorkflowEntity> getById(Long id) {
        return workflowRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<AutomationWorkflowEntity> getActiveByKey(String key) {
        return workflowRepository.findFirstByKeyAndStatusOrderByIdDesc(key, WorkflowStatus.ACTIVE);
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

    public record CreateResult(CreateStatus status, AutomationWorkflowEntity workflow, String errorMessage) {
    }

    public enum CreateStatus {
        SUCCESS,
        INVALID_INPUT,
        INVALID_GRAPH
    }
}
