package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

public record PolicyExecutionPlanRequest(
        WebhookSource sourceSystem,
        String eventId,
        String payloadHash,
        String sourceLeadId,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        String policyDomain,
        String policyKey,
        Long webhookEventId,
        Map<String, Object> metadata) {
}
