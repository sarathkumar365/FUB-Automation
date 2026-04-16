package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.PageResponse;
import com.fuba.automation_engine.controller.dto.WorkflowRunSummary;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.ListRunsResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.ListRunsStatus;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.RunDetailResult;
import com.fuba.automation_engine.service.workflow.WorkflowRunQueryService.RunDetailStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminWorkflowRunController {

    private final WorkflowRunQueryService workflowRunQueryService;

    public AdminWorkflowRunController(WorkflowRunQueryService workflowRunQueryService) {
        this.workflowRunQueryService = workflowRunQueryService;
    }

    @GetMapping("/workflows/{key}/runs")
    public ResponseEntity<?> listRunsForWorkflow(
            @PathVariable String key,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        ListRunsResult result = workflowRunQueryService.listRunsForKey(key, status, page, size);
        if (result.status() == ListRunsStatus.INVALID_INPUT) {
            return ResponseEntity.badRequest().body(result.errorMessage());
        }
        return ResponseEntity.ok(toPageResponse(result));
    }

    @GetMapping("/workflow-runs")
    public ResponseEntity<?> listRunsCrossWorkflow(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        ListRunsResult result = workflowRunQueryService.listRunsCrossWorkflow(status, page, size);
        if (result.status() == ListRunsStatus.INVALID_INPUT) {
            return ResponseEntity.badRequest().body(result.errorMessage());
        }
        return ResponseEntity.ok(toPageResponse(result));
    }

    @GetMapping("/workflow-runs/{runId}")
    public ResponseEntity<?> getRunDetail(@PathVariable Long runId) {
        RunDetailResult result = workflowRunQueryService.getRunDetail(runId);
        if (result.status() == RunDetailStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.errorMessage());
        }
        if (result.status() == RunDetailStatus.INVALID_INPUT) {
            return ResponseEntity.badRequest().body(result.errorMessage());
        }
        return ResponseEntity.ok(result.detail());
    }

    private PageResponse<WorkflowRunSummary> toPageResponse(ListRunsResult result) {
        return new PageResponse<>(
                result.page().items(),
                result.page().page(),
                result.page().size(),
                result.page().total());
    }
}
