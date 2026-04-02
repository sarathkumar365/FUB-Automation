package com.fuba.automation_engine.controller.dto;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;

public record PolicyExecutionRunListItemResponse(
        Long id,
        WebhookSource source,
        String eventId,
        String sourceLeadId,
        String internalLeadRef,
        String domain,
        String policyKey,
        Long policyVersion,
        PolicyExecutionRunStatus status,
        String reasonCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
