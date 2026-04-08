package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

public record PolicyStepExecutionContext(
        long stepId,
        long runId,
        PolicyStepType stepType,
        WebhookSource sourceSystem,
        String sourceLeadId,
        Map<String, Object> policyBlueprintSnapshot,
        PolicyExecutionStepClaimRepository.ClaimedStepRow claimedStep) {
}
