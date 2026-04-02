package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.PolicyExecutionRunDetailResponse;
import com.fuba.automation_engine.controller.dto.PolicyExecutionRunPageResponse;
import com.fuba.automation_engine.exception.policy.InvalidPolicyExecutionQueryException;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.service.policy.AdminPolicyExecutionService;
import com.fuba.automation_engine.service.policy.AdminPolicyExecutionService.PolicyExecutionFeedQuery;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/admin/policy-executions")
public class AdminPolicyExecutionController {

    private final AdminPolicyExecutionService adminPolicyExecutionService;

    public AdminPolicyExecutionController(AdminPolicyExecutionService adminPolicyExecutionService) {
        this.adminPolicyExecutionService = adminPolicyExecutionService;
    }

    @GetMapping
    public ResponseEntity<PolicyExecutionRunPageResponse> list(
            @RequestParam(required = false) PolicyExecutionRunStatus status,
            @RequestParam(required = false) String policyKey,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        PolicyExecutionRunPageResponse response = adminPolicyExecutionService.list(new PolicyExecutionFeedQuery(
                status,
                policyKey,
                from,
                to,
                limit,
                cursor));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PolicyExecutionRunDetailResponse> detail(@PathVariable long id) {
        return adminPolicyExecutionService.findDetail(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Policy execution run not found"));
    }

    @ExceptionHandler(InvalidPolicyExecutionQueryException.class)
    public ResponseEntity<String> handleInvalidQuery(InvalidPolicyExecutionQueryException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
