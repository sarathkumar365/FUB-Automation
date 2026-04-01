package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.persistence.entity.WebhookEventEntity;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.service.webhook.dispatch.WebhookDispatcher;
import com.fuba.automation_engine.service.webhook.live.WebhookLiveFeedPublisher;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookLiveFeedEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.webhook.parse.WebhookParser;
import com.fuba.automation_engine.service.webhook.security.WebhookSignatureVerifier;
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
                "hash");
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
