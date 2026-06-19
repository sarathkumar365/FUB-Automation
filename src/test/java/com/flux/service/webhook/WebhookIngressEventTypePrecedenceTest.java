package com.flux.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flux.config.WebhookProperties;
import com.flux.persistence.entity.WebhookEventEntity;
import com.flux.persistence.repository.WebhookEventRepository;
import com.flux.service.webhook.dispatch.WebhookDispatcher;
import com.flux.service.webhook.live.WebhookLiveFeedPublisher;
import com.flux.service.webhook.model.NormalizedAction;
import com.flux.service.webhook.model.NormalizedDomain;
import com.flux.service.webhook.model.NormalizedWebhookEvent;
import com.flux.service.webhook.model.WebhookEventStatus;
import com.flux.service.webhook.model.WebhookLiveFeedEvent;
import com.flux.service.webhook.model.WebhookSource;
import com.flux.service.webhook.parse.WebhookParser;
import com.flux.service.webhook.security.WebhookSignatureVerifier;
import com.flux.service.webhook.support.StaticWebhookEventSupportResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebhookIngressEventTypePrecedenceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestWebhookParser parser;
    private TestWebhookRepositoryState repositoryState;
    private WebhookIngressService webhookIngressService;

    @BeforeEach
    void setUp() {
        parser = new TestWebhookParser();
        repositoryState = new TestWebhookRepositoryState();

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMaxBodyBytes(1024 * 1024);
        webhookProperties.getSources().getFub().setEnabled(true);

        webhookIngressService = new WebhookIngressService(
                List.of(new AlwaysValidSignatureVerifier()),
                List.of(parser),
                createRepositoryProxy(repositoryState),
                new StaticWebhookEventSupportResolver(),
                new NoopDispatcher(),
                new NoopLiveFeedPublisher(),
                webhookProperties);
    }

    @Test
    void shouldPreferTopLevelSourceEventTypeOverPayloadEventType() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "payloadType");
        parser.eventToReturn = event("sourceType", payload);

        webhookIngressService.ingest("fub", "{}", Map.of("FUB-Signature", "sig"));

        assertEquals("sourceType", repositoryState.lastSavedEntity.getEventType());
    }

    @Test
    void shouldFallbackToPayloadEventTypeWhenTopLevelSourceEventTypeBlank() {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("eventType", "payloadFallback");
        parser.eventToReturn = event("", payload);

        webhookIngressService.ingest("fub", "{}", Map.of("FUB-Signature", "sig"));

        assertEquals("payloadFallback", repositoryState.lastSavedEntity.getEventType());
    }

    private NormalizedWebhookEvent event(String sourceEventType, ObjectNode payload) {
        return new NormalizedWebhookEvent(
                WebhookSource.FUB,
                "evt-1",
                sourceEventType,
                null,
                null,
                NormalizedDomain.UNKNOWN,
                NormalizedAction.UNKNOWN,
                null,
                WebhookEventStatus.RECEIVED,
                payload,
                OffsetDateTime.now(),
                "hash",
                null);
    }

    private WebhookEventRepository createRepositoryProxy(TestWebhookRepositoryState state) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "existsBySourceAndEventId", "existsBySourceAndPayloadHash" -> false;
            case "save" -> {
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

    private static class AlwaysValidSignatureVerifier implements WebhookSignatureVerifier {
        @Override
        public boolean supports(WebhookSource source) {
            return source == WebhookSource.FUB;
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
            return source == WebhookSource.FUB;
        }

        @Override
        public NormalizedWebhookEvent parse(String rawBody, Map<String, String> headers) {
            return eventToReturn;
        }
    }

    private static class NoopDispatcher implements WebhookDispatcher {
        @Override
        public void dispatch(NormalizedWebhookEvent event) {
        }
    }

    private static class NoopLiveFeedPublisher implements WebhookLiveFeedPublisher {
        @Override
        public void publish(WebhookLiveFeedEvent event) {
        }
    }

    private static class TestWebhookRepositoryState {
        private final AtomicLong idSequence = new AtomicLong(1);
        private WebhookEventEntity lastSavedEntity;
    }
}
