package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.CreateWorkflowRequest;
import com.fuba.automation_engine.controller.dto.PageResponse;
import com.fuba.automation_engine.controller.dto.RollbackWorkflowRequest;
import com.fuba.automation_engine.controller.dto.StepTypeCatalogEntry;
import com.fuba.automation_engine.controller.dto.TriggerTypeCatalogEntry;
import com.fuba.automation_engine.controller.dto.UpdateWorkflowRequest;
import com.fuba.automation_engine.controller.dto.ValidateWorkflowRequest;
import com.fuba.automation_engine.controller.dto.ValidateWorkflowResponse;
import com.fuba.automation_engine.controller.dto.WorkflowResponse;
import com.fuba.automation_engine.controller.dto.WorkflowVersionSummary;
import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ActivateResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ActivateStatus;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ArchiveResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ArchiveStatus;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.CreateResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.CreateStatus;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.DeactivateResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.DeactivateStatus;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ListResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.ListStatus;
import com.fuba.automation_engine.service.workflow.RetryPolicy;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.UpdateResult;
import com.fuba.automation_engine.service.workflow.AutomationWorkflowService.UpdateStatus;
import com.fuba.automation_engine.service.workflow.WorkflowStepRegistry;
import com.fuba.automation_engine.service.workflow.trigger.WorkflowTriggerRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workflows")
public class AdminWorkflowController {

    private final AutomationWorkflowService workflowService;
    private final WorkflowStepRegistry stepRegistry;
    private final WorkflowTriggerRegistry triggerRegistry;

    public AdminWorkflowController(
            AutomationWorkflowService workflowService,
            WorkflowStepRegistry stepRegistry,
            WorkflowTriggerRegistry triggerRegistry) {
        this.workflowService = workflowService;
        this.stepRegistry = stepRegistry;
        this.triggerRegistry = triggerRegistry;
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

    @PostMapping("/validate")
    public ResponseEntity<?> validateWorkflow(@RequestBody(required = false) ValidateWorkflowRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid validation request");
        }
        var result = workflowService.validate(request.graph(), request.trigger());
        return ResponseEntity.ok(new ValidateWorkflowResponse(result.valid(), result.errors()));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable Long id) {
        Optional<AutomationWorkflowEntity> workflowOpt = workflowService.getById(id);
        if (workflowOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow not found");
        }
        return ResponseEntity.ok(toResponse(workflowOpt.get()));
    }

    @GetMapping("/{key}")
    public ResponseEntity<?> getWorkflowByKey(@PathVariable String key) {
        Optional<AutomationWorkflowEntity> workflowOpt = workflowService.getLatestByKey(key);
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

    @GetMapping("/trigger-types")
    public ResponseEntity<?> getTriggerTypes() {
        List<TriggerTypeCatalogEntry> catalog = triggerRegistry.allTypes().stream()
                .map(triggerType -> new TriggerTypeCatalogEntry(
                        triggerType.id(),
                        triggerType.displayName(),
                        triggerType.description(),
                        triggerType.configSchema()))
                .toList();
        return ResponseEntity.ok(catalog);
    }

    @GetMapping
    public ResponseEntity<?> listWorkflows(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        int normalizedPage = page != null ? Math.max(0, page) : 0;
        int normalizedSize = size != null ? Math.max(1, size) : 20;

        ListResult result = workflowService.list(status, normalizedPage, normalizedSize);
        if (result.status() == ListStatus.INVALID_INPUT) {
            return ResponseEntity.badRequest().body(result.errorMessage());
        }

        PageResponse<WorkflowResponse> response = new PageResponse<>(
                result.page().items().stream().map(this::toResponse).toList(),
                result.page().page(),
                result.page().size(),
                result.page().total());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> updateWorkflow(@PathVariable String key, @RequestBody(required = false) UpdateWorkflowRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid workflow request");
        }

        UpdateResult result = workflowService.update(key, request.name(), request.description(), request.graph());
        return toUpdateResponse(result);
    }

    @GetMapping("/{key}/versions")
    public ResponseEntity<?> listWorkflowVersions(@PathVariable String key) {
        List<AutomationWorkflowEntity> versions = workflowService.listVersions(key);
        if (versions.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow not found");
        }
        List<WorkflowVersionSummary> response = versions.stream()
                .map(this::toVersionSummary)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{key}/activate")
    public ResponseEntity<?> activateWorkflow(@PathVariable String key) {
        ActivateResult result = workflowService.activate(key);
        return toActivateResponse(result);
    }

    @PostMapping("/{key}/deactivate")
    public ResponseEntity<?> deactivateWorkflow(@PathVariable String key) {
        DeactivateResult result = workflowService.deactivate(key);
        return toDeactivateResponse(result);
    }

    @PostMapping("/{key}/rollback")
    public ResponseEntity<?> rollbackWorkflow(
            @PathVariable String key,
            @RequestBody(required = false) RollbackWorkflowRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid rollback request");
        }
        UpdateResult result = workflowService.rollback(key, request.toVersion());
        return toUpdateResponse(result);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> archiveWorkflow(@PathVariable String key) {
        ArchiveResult result = workflowService.archive(key);
        return toArchiveResponse(result);
    }

    private ResponseEntity<?> toUpdateResponse(UpdateResult result) {
        if (result.status() == UpdateStatus.SUCCESS) {
            return ResponseEntity.ok(toResponse(result.workflow()));
        }
        if (result.status() == UpdateStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.errorMessage());
        }
        return ResponseEntity.badRequest().body(result.errorMessage());
    }

    private ResponseEntity<?> toActivateResponse(ActivateResult result) {
        if (result.status() == ActivateStatus.SUCCESS) {
            return ResponseEntity.ok(toResponse(result.workflow()));
        }
        if (result.status() == ActivateStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.errorMessage());
        }
        return ResponseEntity.badRequest().body(result.errorMessage());
    }

    private ResponseEntity<?> toDeactivateResponse(DeactivateResult result) {
        if (result.status() == DeactivateStatus.SUCCESS) {
            return ResponseEntity.ok(toResponse(result.workflow()));
        }
        if (result.status() == DeactivateStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.errorMessage());
        }
        return ResponseEntity.badRequest().body(result.errorMessage());
    }

    private ResponseEntity<?> toArchiveResponse(ArchiveResult result) {
        if (result.status() == ArchiveStatus.SUCCESS) {
            return ResponseEntity.ok(toResponse(result.workflow()));
        }
        if (result.status() == ArchiveStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.errorMessage());
        }
        return ResponseEntity.badRequest().body(result.errorMessage());
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
                entity.getVersionNumber(),
                entity.getVersion());
    }

    private WorkflowVersionSummary toVersionSummary(AutomationWorkflowEntity entity) {
        return new WorkflowVersionSummary(
                entity.getVersionNumber(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
