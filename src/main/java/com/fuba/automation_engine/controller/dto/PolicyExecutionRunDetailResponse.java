package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PolicyExecutionRunDetailResponse(
        Long id,
        WebhookSource source,
        String eventId,
        Long webhookEventId,
        String sourceLeadId,
        String internalLeadRef,
        String domain,
        String policyKey,
        Long policyVersion,
        Map<String, Object> policyBlueprintSnapshot,
        PolicyExecutionRunStatus status,
        String reasonCode,
        String idempotencyKey,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PolicyExecutionStepResponse> steps) {
}
