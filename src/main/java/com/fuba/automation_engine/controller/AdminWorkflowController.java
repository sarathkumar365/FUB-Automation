package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.CreateWorkflowRequest;
import com.fuba.automation_engine.controller.dto.StepTypeCatalogEntry;
import com.fuba.automation_engine.controller.dto.WorkflowResponse;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.CreateResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.CreateStatus;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.WorkflowStepRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workflows")
public class AdminWorkflowController {

    private final AutomationWorkflowService workflowService;
    private final WorkflowStepRegistry stepRegistry;

    public AdminWorkflowController(AutomationWorkflowService workflowService, WorkflowStepRegistry stepRegistry) {
        this.workflowService = workflowService;
        this.stepRegistry = stepRegistry;
    }

    @PostMapping
    public ResponseEntity<?> createWorkflow(@RequestBody(required = false) CreateWorkflowRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid workflow request");
        }
        CreateResult result = workflowService.create(
                request.key(), request.name(), request.description(), request.graph(), request.status());
        if (result.status() == CreateStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result.workflow()));
        }
        if (result.status() == CreateStatus.INVALID_GRAPH) {
            return ResponseEntity.badRequest().body(result.errorMessage());
        }
        return ResponseEntity.badRequest().body(result.errorMessage());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable Long id) {
        Optional<AutomationWorkflowEntity> workflowOpt = workflowService.getById(id);
        if (workflowOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow not found");
        }
        return ResponseEntity.ok(toResponse(workflowOpt.get()));
    }

    @GetMapping("/step-types")
    public ResponseEntity<?> getStepTypes() {
        List<StepTypeCatalogEntry> catalog = stepRegistry.allTypes().stream()
                .map(st -> new StepTypeCatalogEntry(
                        st.id(),
                        st.displayName(),
                        st.description(),
                        st.configSchema(),
                        st.declaredResultCodes(),
                        retryPolicyMap(st.defaultRetryPolicy())))
                .toList();
        return ResponseEntity.ok(catalog);
    }

    private Map<String, Object> retryPolicyMap(RetryPolicy retryPolicy) {
        RetryPolicy policy = retryPolicy != null ? retryPolicy : RetryPolicy.NO_RETRY;
        return Map.of(
                "maxAttempts", policy.maxAttempts(),
                "initialBackoffMs", policy.initialBackoffMs(),
                "backoffMultiplier", policy.backoffMultiplier(),
                "maxBackoffMs", policy.maxBackoffMs(),
                "retryOnTransient", policy.retryOnTransient());
    }

    private WorkflowResponse toResponse(AutomationWorkflowEntity entity) {
        return new WorkflowResponse(
                entity.getId(),
                entity.getKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getTrigger(),
                entity.getGraph(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getVersion());
    }
}
