package com.fuba.automation_engine.service.webhook.support;

import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StaticWebhookEventSupportResolver implements WebhookEventSupportResolver {

    private static final EventSupportResolution DEFAULT_RESOLUTION = new EventSupportResolution(
            EventSupportState.IGNORED,
            NormalizedDomain.UNKNOWN,
            NormalizedAction.UNKNOWN,
            "Default fallback for unmapped source event type");

    private static final Map<ResolverKey, EventSupportResolution> RESOLUTIONS = Map.of(
            new ResolverKey(WebhookSource.FUB, "callsCreated"),
            new EventSupportResolution(
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.CALL,
                    NormalizedAction.CREATED,
                    "Batch 1 supported call event"),
            new ResolverKey(WebhookSource.FUB, "peopleCreated"),
            new EventSupportResolution(
                    EventSupportState.STAGED,
                    NormalizedDomain.ASSIGNMENT,
                    NormalizedAction.CREATED,
                    "Batch 1 staged assignment create event"),
            new ResolverKey(WebhookSource.FUB, "peopleUpdated"),
            new EventSupportResolution(
                    EventSupportState.STAGED,
                    NormalizedDomain.ASSIGNMENT,
                    NormalizedAction.UPDATED,
                    "Batch 1 staged assignment update event"));

    @Override
    public EventSupportResolution resolve(WebhookSource sourceSystem, String sourceEventType) {
        if (sourceSystem == null) {
            return DEFAULT_RESOLUTION;
        }
        String normalizedEventType = sourceEventType == null ? "" : sourceEventType.trim();
        if (normalizedEventType.isEmpty()) {
            return DEFAULT_RESOLUTION;
        }
        return RESOLUTIONS.getOrDefault(new ResolverKey(sourceSystem, normalizedEventType), DEFAULT_RESOLUTION);
    }

    private record ResolverKey(WebhookSource sourceSystem, String sourceEventType) {
    }
}
