package com.fuba.automation_engine.service.webhook.live;

import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WebhookSseHub {

    private static final Logger log = LoggerFactory.getLogger(WebhookSseHub.class);
    private static final String EVENT_WEBHOOK_RECEIVED = "webhook.received";
    private static final String EVENT_HEARTBEAT = "heartbeat";

    private final Map<Long, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong subscriberIdSequence = new AtomicLong(0);
    private final Clock clock;
    private final int heartbeatSeconds;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "webhook-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final long emitterTimeoutMs;

    public WebhookSseHub(WebhookProperties webhookProperties, Clock clock) {
        this.clock = clock;
        this.heartbeatSeconds = Math.max(1, webhookProperties.getLiveFeed().getHeartbeatSeconds());
        this.emitterTimeoutMs = webhookProperties.getLiveFeed().getEmitterTimeoutMs();
    }

    @PostConstruct
    void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, heartbeatSeconds, heartbeatSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        subscribers.values().forEach(subscriber -> subscriber.emitter().complete());
        subscribers.clear();
    }

    public SseEmitter subscribe(WebhookStreamFilter filter) {
        SseEmitter emitter = createEmitter();
        long subscriberId = subscriberIdSequence.incrementAndGet();

        WebhookStreamFilter normalizedFilter = normalizeFilter(filter);
        subscribers.put(subscriberId, new Subscriber(subscriberId, normalizedFilter, emitter));
        log.info(
                "Webhook stream subscriber connected id={} source={} status={} eventType={} activeSubscribers={}",
                subscriberId,
                normalizedFilter.source(),
                normalizedFilter.status(),
                normalizedFilter.eventType(),
                subscribers.size());
        emitter.onCompletion(() -> removeSubscriber(subscriberId));
        emitter.onTimeout(() -> {
            removeSubscriber(subscriberId);
            emitter.complete();
        });
        emitter.onError(ex -> removeSubscriber(subscriberId));
        return emitter;
    }

    public void publish(WebhookLiveFeedEvent event) {
        if (event == null) {
            return;
        }

        // TODO: Replace Map.of with a null-tolerant payload builder (for example LinkedHashMap)
        // because webhook fields like eventId can be null and Map.of throws NullPointerException.
        Map<String, Object> data = Map.of(
                "id", event.id(),
                "eventId", event.eventId(),
                "source", event.source(),
                "eventType", event.eventType(),
                "status", event.status(),
                "receivedAt", event.receivedAt());

        int[] delivered = new int[1];
        subscribers.values().forEach(subscriber -> {
            if (matches(subscriber.filter(), event)) {
                sendToSubscriber(subscriber, EVENT_WEBHOOK_RECEIVED, data);
                delivered[0]++;
            }
        });
        log.info(
                "Webhook stream event published id={} eventId={} source={} eventType={} deliveredSubscribers={} activeSubscribers={}",
                event.id(),
                event.eventId(),
                event.source(),
                event.eventType(),
                delivered[0],
                subscribers.size());
    }

    void sendHeartbeat() {
        Map<String, Object> heartbeat = Map.of("serverTime", OffsetDateTime.now(clock));
        subscribers.values().forEach(subscriber -> sendToSubscriber(subscriber, EVENT_HEARTBEAT, heartbeat));
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(emitterTimeoutMs);
    }

    protected void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private void sendToSubscriber(Subscriber subscriber, String eventName, Object data) {
        try {
            sendEvent(subscriber.emitter(), eventName, data);
        } catch (IOException | IllegalStateException ex) {
            log.debug("Removing SSE subscriber after send failure id={} event={}", subscriber.id(), eventName, ex);
            removeSubscriber(subscriber.id());
            subscriber.emitter().completeWithError(ex);
        }
    }

    private boolean matches(WebhookStreamFilter filter, WebhookLiveFeedEvent event) {
        if (filter.source() != null && filter.source() != event.source()) {
            return false;
        }
        if (filter.status() != null && filter.status() != event.status()) {
            return false;
        }
        if (filter.eventType() != null && !filter.eventType().equals(event.eventType())) {
            return false;
        }
        return true;
    }

    private WebhookStreamFilter normalizeFilter(WebhookStreamFilter filter) {
        if (filter == null) {
            return new WebhookStreamFilter(null, null, null);
        }
        String normalizedEventType = filter.eventType();
        if (normalizedEventType != null) {
            normalizedEventType = normalizedEventType.trim();
            if (normalizedEventType.isBlank()) {
                normalizedEventType = null;
            }
        }
        return new WebhookStreamFilter(filter.source(), filter.status(), normalizedEventType);
    }

    private void removeSubscriber(long subscriberId) {
        Subscriber removed = subscribers.remove(subscriberId);
        if (removed != null) {
            log.info("Webhook stream subscriber disconnected id={} activeSubscribers={}", subscriberId, subscribers.size());
        }
    }

    private record Subscriber(long id, WebhookStreamFilter filter, SseEmitter emitter) {
    }

    public record WebhookStreamFilter(
            WebhookSource source,
            WebhookEventStatus status,
            String eventType) {
    }
}
