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
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.PERSON,
                    NormalizedAction.CREATED,
                    "Supported person create event"),
            new ResolverKey(WebhookSource.FUB, "peopleUpdated"),
            new EventSupportResolution(
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.PERSON,
                    NormalizedAction.UPDATED,
                    "Supported person update event"),
            new ResolverKey(WebhookSource.FUB, "notesCreated"),
            new EventSupportResolution(
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.NOTE,
                    NormalizedAction.CREATED,
                    "Supported note create event"),
            new ResolverKey(WebhookSource.FUB, "notesUpdated"),
            new EventSupportResolution(
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.NOTE,
                    NormalizedAction.UPDATED,
                    "Supported note update event"),
            new ResolverKey(WebhookSource.FUB, "notesDeleted"),
            new EventSupportResolution(
                    EventSupportState.SUPPORTED,
                    NormalizedDomain.NOTE,
                    NormalizedAction.DELETED,
                    "Supported note delete event"));

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
