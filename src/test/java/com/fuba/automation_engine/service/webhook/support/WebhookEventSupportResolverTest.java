package com.fuba.automation_engine.service.webhook.support;

import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookEventSupportResolverTest {

    private final WebhookEventSupportResolver resolver = new StaticWebhookEventSupportResolver();

    @Test
    void shouldResolveExplicitFubEntries() {
        EventSupportResolution callsCreated = resolver.resolve(WebhookSource.FUB, "callsCreated");
        EventSupportResolution peopleCreated = resolver.resolve(WebhookSource.FUB, "peopleCreated");
        EventSupportResolution peopleUpdated = resolver.resolve(WebhookSource.FUB, "peopleUpdated");

        assertEquals(EventSupportState.SUPPORTED, callsCreated.supportState());
        assertEquals(NormalizedDomain.CALL, callsCreated.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, callsCreated.normalizedAction());

        assertEquals(EventSupportState.SUPPORTED, peopleCreated.supportState());
        assertEquals(NormalizedDomain.ASSIGNMENT, peopleCreated.normalizedDomain());
        assertEquals(NormalizedAction.CREATED, peopleCreated.normalizedAction());

        assertEquals(EventSupportState.SUPPORTED, peopleUpdated.supportState());
        assertEquals(NormalizedDomain.ASSIGNMENT, peopleUpdated.normalizedDomain());
        assertEquals(NormalizedAction.UPDATED, peopleUpdated.normalizedAction());
    }

    @Test
    void shouldFallbackToIgnoredUnknownUnknownForUnmappedEvents() {
        EventSupportResolution unknownFub = resolver.resolve(WebhookSource.FUB, "callsDeleted");
        EventSupportResolution unknownInternal = resolver.resolve(WebhookSource.INTERNAL, "peopleCreated");
        EventSupportResolution blankEventType = resolver.resolve(WebhookSource.FUB, "  ");

        assertEquals(EventSupportState.IGNORED, unknownFub.supportState());
        assertEquals(NormalizedDomain.UNKNOWN, unknownFub.normalizedDomain());
        assertEquals(NormalizedAction.UNKNOWN, unknownFub.normalizedAction());

        assertEquals(EventSupportState.IGNORED, unknownInternal.supportState());
        assertEquals(NormalizedDomain.UNKNOWN, unknownInternal.normalizedDomain());
        assertEquals(NormalizedAction.UNKNOWN, unknownInternal.normalizedAction());

        assertEquals(EventSupportState.IGNORED, blankEventType.supportState());
        assertEquals(NormalizedDomain.UNKNOWN, blankEventType.normalizedDomain());
        assertEquals(NormalizedAction.UNKNOWN, blankEventType.normalizedAction());
    }

    @Test
    void shouldBeDeterministicForRepeatedLookups() {
        EventSupportResolution first = resolver.resolve(WebhookSource.FUB, "callsCreated");
        EventSupportResolution second = resolver.resolve(WebhookSource.FUB, "callsCreated");

        assertEquals(first, second);
    }
}
