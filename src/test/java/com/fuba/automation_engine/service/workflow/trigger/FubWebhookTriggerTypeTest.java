package com.fuba.automation_engine.service.workflow.trigger;

import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import com.fuba.automation_engine.service.workflow.expression.JsonataExpressionEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookTriggerTypeTest {

    private final FubWebhookTriggerType triggerType = new FubWebhookTriggerType(new JsonataExpressionEvaluator());

    @Test
    void shouldMatchExactDomainAndAction() {
        TriggerMatchContext context = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                Map.of("eventDomain", "ASSIGNMENT", "eventAction", "CREATED"),
                Map.of("resourceIds", List.of(1, 2)));

        assertTrue(triggerType.matches(context));
    }

    @Test
    void shouldMatchWithWildcardDomainAndAction() {
        TriggerMatchContext context = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                Map.of("eventDomain", "*", "eventAction", "*"),
                Map.of("resourceIds", List.of(1)));

        assertTrue(triggerType.matches(context));
    }

    @Test
    void shouldNotMatchWhenSourceIsNotFub() {
        TriggerMatchContext context = new TriggerMatchContext(
                WebhookSource.INTERNAL,
                "peopleUpdated",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                Map.of(),
                Map.of("eventDomain", "*", "eventAction", "*"));

        assertFalse(triggerType.matches(context));
    }

    @Test
    void shouldNotMatchWhenDomainOrActionMismatch() {
        TriggerMatchContext context = context(
                NormalizedDomain.CALL,
                NormalizedAction.CREATED,
                Map.of("eventDomain", "ASSIGNMENT", "eventAction", "UPDATED"),
                Map.of("resourceIds", List.of(1)));

        assertFalse(triggerType.matches(context));
    }

    @Test
    void shouldApplyFilterWhenPresent() {
        TriggerMatchContext matching = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                Map.of(
                        "eventDomain", "ASSIGNMENT",
                        "eventAction", "CREATED",
                        "filter", "event.payload.channel = \"zillow\""),
                Map.of("channel", "zillow", "resourceIds", List.of(10)));
        TriggerMatchContext nonMatching = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                Map.of(
                        "eventDomain", "ASSIGNMENT",
                        "eventAction", "CREATED",
                        "filter", "event.payload.channel = \"zillow\""),
                Map.of("channel", "manual", "resourceIds", List.of(10)));

        assertTrue(triggerType.matches(matching));
        assertFalse(triggerType.matches(nonMatching));
    }

    @Test
    void shouldReturnFalseWhenFilterPathMissing() {
        TriggerMatchContext context = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                Map.of(
                        "eventDomain", "ASSIGNMENT",
                        "eventAction", "CREATED",
                        "filter", "event.payload.metadata.source = \"zillow\""),
                Map.of("resourceIds", List.of(10)));

        assertFalse(triggerType.matches(context));
    }

    @Test
    void shouldExtractLeadEntitiesFromResourceIds() {
        List<Object> resourceIds = new ArrayList<>(List.of(777, "778", " "));
        resourceIds.add(null);
        TriggerMatchContext context = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                Map.of("eventDomain", "*", "eventAction", "*"),
                Map.of("resourceIds", resourceIds));

        List<EntityRef> entities = triggerType.extractEntities(context);

        assertEquals(2, entities.size());
        assertEquals(new EntityRef("lead", "777"), entities.get(0));
        assertEquals(new EntityRef("lead", "778"), entities.get(1));
    }

    @Test
    void shouldReturnEmptyEntitiesWhenResourceIdsMissingOrNotArray() {
        TriggerMatchContext missing = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                Map.of("eventDomain", "*", "eventAction", "*"),
                Map.of());
        TriggerMatchContext invalid = context(
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.UPDATED,
                Map.of("eventDomain", "*", "eventAction", "*"),
                Map.of("resourceIds", "not-a-list"));

        assertTrue(triggerType.extractEntities(missing).isEmpty());
        assertTrue(triggerType.extractEntities(invalid).isEmpty());
    }

    private TriggerMatchContext context(
            NormalizedDomain domain,
            NormalizedAction action,
            Map<String, Object> triggerConfig,
            Map<String, Object> payload) {
        return new TriggerMatchContext(
                WebhookSource.FUB,
                "peopleUpdated",
                domain,
                action,
                payload,
                triggerConfig);
    }
}
