package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

public record TriggerMatchContext(
        WebhookSource source,
        String eventType,
        NormalizedDomain normalizedDomain,
        NormalizedAction normalizedAction,
        Map<String, Object> payload,
        Map<String, Object> triggerConfig) {
}
