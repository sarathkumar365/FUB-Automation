package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.dispatch.WebhookDispatcher;
import com.fuba.automation_engine.service.webhook.live.WebhookLiveFeedPublisher;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookIngressResult;
import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.webhook.parse.WebhookParser;
import com.fuba.automation_engine.service.webhook.security.WebhookSignatureVerifier;
import com.fuba.automation_engine.service.webhook.support.StaticWebhookEventSupportResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebhookIngressServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestWebhookParser parser;
    private TestWebhookRepositoryState repositoryState;
    private TestWebhookDispatcher dispatcher;
    private TestWebhookLiveFeedPublisher liveFeedPublisher;
    private WebhookIngressService webhookIngressService;

    @BeforeEach
    void setUp() {
        parser = new TestWebhookParser();
        repositoryState = new TestWebhookRepositoryState();
        dispatcher = new TestWebhookDispatcher();
        liveFeedPublisher = new TestWebhookLiveFeedPublisher();

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMaxBodyBytes(1024 * 1024);
        webhookProperties.getSources().getFub().setEnabled(true);

        webhookIngressService = new WebhookIngressService(
                List.of(new AlwaysValidSignatureVerifier()),
                List.of(parser),
                createRepositoryProxy(repositoryState),
                new StaticWebhookEventSupportResolver(),
                dispatcher,
                liveFeedPublisher,
                webhookProperties);
    }

    @Test
    void shouldPersistUnknownEventTypeWhenMissingFromPayload() {
        parser.eventToReturn = new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-missing-event-type",
                "",
                null,
                null,
                NormalizedDomain.UNKNOWN,
                NormalizedAction.UNKNOWN,
                null,
                WebhookEventStatus.RECEIVED,
                OBJECT_MAPPER.createObjectNode(),
                OffsetDateTime.now(),
                "payload-hash-1");

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"callsCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Event type not supported yet: UNKNOWN", result.message());
        assertEquals("UNKNOWN", repositoryState.lastSavedEntity.getEventType());
        assertEquals(0, dispatcher.dispatchCount);
        assertEquals(EventSupportState.IGNORED, repositoryState.lastSavedEntity.getCatalogState());
        assertEquals(NormalizedDomain.UNKNOWN, repositoryState.lastSavedEntity.getNormalizedDomain());
        assertEquals(NormalizedAction.UNKNOWN, repositoryState.lastSavedEntity.getNormalizedAction());
        assertNotNull(liveFeedPublisher.lastPublishedEvent);
    }

    @Test
    void shouldPublishLiveFeedAfterSuccessfulSave() {
        parser.eventToReturn = normalizedEvent("evt-success", "callsCreated", "payload-hash-2");

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"callsCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Webhook accepted for async processing", result.message());
        assertEquals(1, liveFeedPublisher.publishCount);
        assertEquals("callsCreated", liveFeedPublisher.lastPublishedEvent.eventType());
        assertEquals(1, dispatcher.dispatchCount);
        assertEquals(EventSupportState.SUPPORTED, repositoryState.lastSavedEntity.getCatalogState());
        assertEquals(NormalizedDomain.CALL, repositoryState.lastSavedEntity.getNormalizedDomain());
        assertEquals(NormalizedAction.CREATED, repositoryState.lastSavedEntity.getNormalizedAction());
    }

    @Test
    void shouldDispatchAssignmentEventsWhenSupported() {
        parser.eventToReturn = new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-staged-1",
                "peopleCreated",
                null,
                "lead-123",
                NormalizedDomain.UNKNOWN,
                NormalizedAction.UNKNOWN,
                null,
                WebhookEventStatus.RECEIVED,
                OBJECT_MAPPER.createObjectNode(),
                OffsetDateTime.now(),
                "payload-hash-staged-1");

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"peopleCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Webhook accepted for async processing", result.message());
        assertEquals(1, liveFeedPublisher.publishCount);
        assertEquals(1, dispatcher.dispatchCount);
        assertEquals(EventSupportState.SUPPORTED, repositoryState.lastSavedEntity.getCatalogState());
        assertEquals(NormalizedDomain.ASSIGNMENT, repositoryState.lastSavedEntity.getNormalizedDomain());
        assertEquals(NormalizedAction.CREATED, repositoryState.lastSavedEntity.getNormalizedAction());
        assertEquals("lead-123", repositoryState.lastSavedEntity.getSourceLeadId());
    }

    @Test
    void shouldNotPublishOrDispatchWhenDuplicateDetectedBeforeSave() {
        parser.eventToReturn = normalizedEvent("evt-dup", "callsCreated", "payload-hash-3");
        repositoryState.existingEventIds.put("evt-dup", true);

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"callsCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Duplicate webhook ignored", result.message());
        assertEquals(0, repositoryState.saveCount);
        assertEquals(0, liveFeedPublisher.publishCount);
        assertEquals(0, dispatcher.dispatchCount);
    }

    @Test
    void shouldNotPublishOrDispatchWhenDuplicateDetectedDuringSave() {
        parser.eventToReturn = normalizedEvent("evt-conflict", "callsCreated", "payload-hash-4");
        repositoryState.throwDuplicateOnSave = true;

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"callsCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Duplicate webhook ignored", result.message());
        assertEquals(1, repositoryState.saveCount);
        assertEquals(0, liveFeedPublisher.publishCount);
        assertEquals(0, dispatcher.dispatchCount);
    }

    @Test
    void shouldContinueWhenLivePublishFails() {
        parser.eventToReturn = normalizedEvent("evt-publish-fail", "callsCreated", "payload-hash-5");
        liveFeedPublisher.failOnPublish = true;

        WebhookIngressResult result = webhookIngressService.ingest("fub", "{\"event\":\"callsCreated\"}", Map.of("FUB-Signature", "sig"));

        assertEquals("Webhook accepted for async processing", result.message());
        assertEquals(1, liveFeedPublisher.publishCount);
        assertEquals(1, dispatcher.dispatchCount);
    }

    private WebhookEventRepository createRepositoryProxy(TestWebhookRepositoryState state) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "existsBySourceAndEventId" -> state.existingEventIds.getOrDefault((String) args[1], false);
            case "existsBySourceAndPayloadHash" -> state.existingPayloadHashes.getOrDefault((String) args[1], false);
            case "save" -> {
                state.saveCount++;
                if (state.throwDuplicateOnSave) {
                    throw new DataIntegrityViolationException("duplicate");
                }
                WebhookEventEntity entity = (WebhookEventEntity) args[0];
                entity.setId(state.idSequence.incrementAndGet());
                state.lastSavedEntity = entity;
                yield entity;
            }
            default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
        };

        return (WebhookEventRepository) Proxy.newProxyInstance(
                WebhookEventRepository.class.getClassLoader(),
                new Class[] {WebhookEventRepository.class},
                handler);
    }

    private NormalizedWebhookEvent normalizedEvent(String eventId, String eventType, String payloadHash) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", eventType);
        payload.putArray("resourceIds").add(123);
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                eventId,
                eventType,
                null,
                null,
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                payloadHash);
    }

    private static class AlwaysValidSignatureVerifier implements WebhookSignatureVerifier {
        @Override
        public boolean supports(WebhookSource source) {
            return WebhookSource.FUB == source;
        }

        @Override
        public boolean verify(String rawBody, Map<String, String> headers) {
            return true;
        }
    }

    private static class TestWebhookParser implements WebhookParser {
        private NormalizedWebhookEvent eventToReturn;

        @Override
        public boolean supports(WebhookSource source) {
            return WebhookSource.FUB == source;
        }

        @Override
        public NormalizedWebhookEvent parse(String rawBody, Map<String, String> headers) {
            return eventToReturn;
        }
    }

    private static class TestWebhookDispatcher implements WebhookDispatcher {
        private int dispatchCount;

        @Override
        public void dispatch(NormalizedWebhookEvent event) {
            dispatchCount++;
        }
    }

    private static class TestWebhookLiveFeedPublisher implements WebhookLiveFeedPublisher {
        private int publishCount;
        private boolean failOnPublish;
        private WebhookLiveFeedEvent lastPublishedEvent;

        @Override
        public void publish(WebhookLiveFeedEvent event) {
            publishCount++;
            lastPublishedEvent = event;
            if (failOnPublish) {
                throw new IllegalStateException("simulated publisher failure");
            }
        }
    }

    private static class TestWebhookRepositoryState {
        private final AtomicLong idSequence = new AtomicLong(50);
        private final Map<String, Boolean> existingEventIds = new java.util.HashMap<>();
        private final Map<String, Boolean> existingPayloadHashes = new java.util.HashMap<>();
        private boolean throwDuplicateOnSave;
        private int saveCount;
        private WebhookEventEntity lastSavedEntity;
    }
}
