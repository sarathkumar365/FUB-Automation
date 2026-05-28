package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record WorkflowPlanRequest(
        String workflowKey,
        String source,
        String eventId,
        Long webhookEventId,
        String sourcePersonId,
        Map<String, Object> triggerPayload) {
}
