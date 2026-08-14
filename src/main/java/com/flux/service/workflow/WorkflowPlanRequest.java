package com.flux.service.workflow;

import java.util.Map;

public record WorkflowPlanRequest(
        String workflowKey,
        String source,
        String eventId,
        Long webhookEventId,
        String sourcePersonId,
        Map<String, Object> triggerPayload,
        Long domainEventId) {

    /** Back-compat shape for callers with no domain event (webhook-path callers, tests). */
    public WorkflowPlanRequest(
            String workflowKey,
            String source,
            String eventId,
            Long webhookEventId,
            String sourcePersonId,
            Map<String, Object> triggerPayload) {
        this(workflowKey, source, eventId, webhookEventId, sourcePersonId, triggerPayload, null);
    }
}
