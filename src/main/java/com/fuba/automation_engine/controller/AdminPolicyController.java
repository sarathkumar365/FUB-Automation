package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.controller.dto.ActivatePolicyRequest;
import com.fuba.automation_engine.controller.dto.CreatePolicyRequest;
import com.fuba.automation_engine.controller.dto.PolicyResponse;
import com.fuba.automation_engine.controller.dto.UpdatePolicyRequest;
import com.fuba.automation_engine.service.policy.AutomationPolicyService;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ActivatePolicyCommand;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.CreatePolicyCommand;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.MutationResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.MutationStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.PolicyView;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ReadStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.UpdatePolicyCommand;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/policies")
public class AdminPolicyController {

    private final AutomationPolicyService automationPolicyService;

    public AdminPolicyController(AutomationPolicyService automationPolicyService) {
        this.automationPolicyService = automationPolicyService;
    }

    @GetMapping("/{domain}/{policyKey}/active")
    public ResponseEntity<?> getActivePolicy(@PathVariable String domain, @PathVariable String policyKey) {
        var result = automationPolicyService.getActivePolicy(domain, policyKey);
        if (result.status() == ReadStatus.SUCCESS) {
            return ResponseEntity.ok(toResponse(result.policy()));
        }
        if (result.status() == ReadStatus.POLICY_INVALID) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body("Active policy blueprint is invalid");
        }
        if (result.status() == ReadStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Active policy not found");
        }
        return ResponseEntity.badRequest().body("Invalid policy scope");
    }

    @GetMapping
    public ResponseEntity<?> listPolicies(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String policyKey) {
        if (domain == null && policyKey == null) {
            List<PolicyResponse> response = automationPolicyService.listAllPolicies()
                    .stream().map(this::toResponse).toList();
            return ResponseEntity.ok(response);
        }
        if (domain == null || policyKey == null) {
            return ResponseEntity.badRequest().body("Both domain and policyKey are required when filtering");
        }
        var result = automationPolicyService.listPolicies(domain, policyKey);
        if (result.status() == ReadStatus.SUCCESS) {
            List<PolicyResponse> response = result.policies().stream().map(this::toResponse).toList();
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body("Invalid policy scope");
    }

    @PostMapping
    public ResponseEntity<?> createPolicy(@RequestBody(required = false) CreatePolicyRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid policy request");
        }
        MutationResult result = automationPolicyService.createPolicy(
                new CreatePolicyCommand(
                        request.domain(),
                        request.policyKey(),
                        request.enabled(),
                        request.blueprint()));
        return toMutationResponse(result, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePolicy(@PathVariable long id, @RequestBody(required = false) UpdatePolicyRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid policy request");
        }
        MutationResult result = automationPolicyService.updatePolicy(
                id,
                new UpdatePolicyCommand(
                        request.enabled(),
                        request.expectedVersion(),
                        request.blueprint()));
        return toMutationResponse(result, HttpStatus.OK);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activatePolicy(@PathVariable long id, @RequestBody(required = false) ActivatePolicyRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Invalid policy request");
        }
        MutationResult result = automationPolicyService.activatePolicy(id, new ActivatePolicyCommand(request.expectedVersion()));
        return toMutationResponse(result, HttpStatus.OK);
    }

    private ResponseEntity<?> toMutationResponse(MutationResult result, HttpStatus successStatus) {
        if (result.status() == MutationStatus.SUCCESS) {
            return ResponseEntity.status(successStatus).body(toResponse(result.policy()));
        }
        if (result.status() == MutationStatus.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Policy not found");
        }
        if (result.status() == MutationStatus.STALE_VERSION) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Stale policy version");
        }
        if (result.status() == MutationStatus.ACTIVE_CONFLICT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Policy activation conflict");
        }
        if (result.status() == MutationStatus.INVALID_POLICY_BLUEPRINT) {
            return ResponseEntity.badRequest().body("Invalid policy blueprint");
        }
        return ResponseEntity.badRequest().body("Invalid policy request");
    }

    private PolicyResponse toResponse(PolicyView view) {
        return new PolicyResponse(
                view.id(),
                view.domain(),
                view.policyKey(),
                view.enabled(),
                view.blueprint(),
                view.status(),
                view.version());
    }
}
