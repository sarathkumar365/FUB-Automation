package com.fuba.automation_engine.service.webhook.live;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.service.webhook.live.WebhookSseHub.WebhookStreamFilter;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSseHubTest {

    private final TestableWebhookSseHub hub = new TestableWebhookSseHub();

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void shouldFanoutOnlyToMatchingFilteredSubscribers() {
        SseEmitter matching = hub.subscribe(new WebhookStreamFilter(WebhookSource.FUB, WebhookEventStatus.RECEIVED, "callsCreated"));
        SseEmitter nonMatching = hub.subscribe(new WebhookStreamFilter(WebhookSource.FUB, null, "callsUpdated"));

        hub.publish(new WebhookLiveFeedEvent(
                42L,
                "evt-live-1",
                WebhookSource.FUB,
                "callsCreated",
                WebhookEventStatus.RECEIVED,
                OffsetDateTime.now()));

        List<CapturedEvent> matchingEvents = hub.eventsFor(matching);
        List<CapturedEvent> nonMatchingEvents = hub.eventsFor(nonMatching);
        assertEquals(1, matchingEvents.size());
        assertEquals("webhook.received", matchingEvents.getFirst().eventName());
        assertEquals(0, nonMatchingEvents.size());
    }

    @Test
    void shouldRemoveSubscriberWhenSendFails() {
        SseEmitter emitter = hub.subscribe(new WebhookStreamFilter(null, null, null));
        hub.failFor(emitter);

        hub.publish(new WebhookLiveFeedEvent(
                51L,
                "evt-live-fail",
                WebhookSource.FUB,
                "callsCreated",
                WebhookEventStatus.RECEIVED,
                OffsetDateTime.now()));

        assertEquals(0, hub.subscriberCount());
    }

    @Test
    void shouldSendHeartbeatEventToSubscribers() {
        SseEmitter emitter = hub.subscribe(new WebhookStreamFilter(null, null, null));

        hub.sendHeartbeat();

        List<CapturedEvent> events = hub.eventsFor(emitter);
        assertEquals(1, events.size());
        assertEquals("heartbeat", events.getFirst().eventName());
        assertTrue(((Map<?, ?>) events.getFirst().data()).containsKey("serverTime"));
    }

    private static class TestableWebhookSseHub extends WebhookSseHub {
        private final Map<SseEmitter, List<CapturedEvent>> capturedEvents = new ConcurrentHashMap<>();
        private final Set<SseEmitter> failingEmitters = ConcurrentHashMap.newKeySet();

        private TestableWebhookSseHub() {
            super(properties(), Clock.fixed(OffsetDateTime.parse("2026-03-18T12:00:00Z").toInstant(), ZoneOffset.UTC));
        }

        @Override
        protected SseEmitter createEmitter() {
            SseEmitter emitter = new SseEmitter(60_000L);
            capturedEvents.put(emitter, new CopyOnWriteArrayList<>());
            return emitter;
        }

        @Override
        protected void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
            if (failingEmitters.contains(emitter)) {
                throw new IOException("simulated send failure");
            }
            capturedEvents.computeIfAbsent(emitter, ignored -> new CopyOnWriteArrayList<>())
                    .add(new CapturedEvent(eventName, data));
        }

        private List<CapturedEvent> eventsFor(SseEmitter emitter) {
            return capturedEvents.getOrDefault(emitter, List.of());
        }

        private void failFor(SseEmitter emitter) {
            failingEmitters.add(emitter);
        }

        private static WebhookProperties properties() {
            WebhookProperties properties = new WebhookProperties();
            properties.getLiveFeed().setHeartbeatSeconds(60);
            properties.getLiveFeed().setEmitterTimeoutMs(60_000L);
            return properties;
        }
    }

    private record CapturedEvent(String eventName, Object data) {
    }
}
