package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.ProcessedCallSummaryResponse;
import com.fuba.automation_engine.controller.dto.ReplayProcessedCallResponse;
import com.fuba.automation_engine.persistence.entity.ProcessedCallEntity;
import com.fuba.automation_engine.persistence.entity.ProcessedCallStatus;
import com.fuba.automation_engine.service.webhook.ProcessedCallAdminService;
import com.fuba.automation_engine.service.webhook.ProcessedCallAdminService.ReplayOutcome;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/processed-calls")
public class ProcessedCallAdminController {

    private final ProcessedCallAdminService processedCallAdminService;

    public ProcessedCallAdminController(ProcessedCallAdminService processedCallAdminService) {
        this.processedCallAdminService = processedCallAdminService;
    }

    @GetMapping
    public ResponseEntity<List<ProcessedCallSummaryResponse>> list(
            @RequestParam(required = false) ProcessedCallStatus status,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer limit) {
        List<ProcessedCallSummaryResponse> response = processedCallAdminService.list(status, from, to, limit).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{callId}/replay")
    public ResponseEntity<ReplayProcessedCallResponse> replay(@PathVariable long callId) {
        ReplayOutcome outcome = processedCallAdminService.replay(callId);
        if (outcome == ReplayOutcome.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Processed call not found");
        }
        if (outcome == ReplayOutcome.NOT_REPLAYABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replay allowed only for FAILED calls");
        }
        return ResponseEntity.accepted().body(new ReplayProcessedCallResponse("Replay accepted"));
    }

    private ProcessedCallSummaryResponse toResponse(ProcessedCallEntity entity) {
        return new ProcessedCallSummaryResponse(
                entity.getCallId(),
                entity.getStatus(),
                entity.getRuleApplied(),
                entity.getTaskId(),
                entity.getFailureReason(),
                entity.getRetryCount(),
                entity.getUpdatedAt());
    }
}
