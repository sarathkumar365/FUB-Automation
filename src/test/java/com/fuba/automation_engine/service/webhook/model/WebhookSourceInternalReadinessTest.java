package com.fuba.automation_engine.service.webhook.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebhookSourceInternalReadinessTest {

    @Test
    void shouldResolveInternalSourceCaseInsensitively() {
        assertEquals(WebhookSource.INTERNAL, WebhookSource.fromPathValue("internal"));
        assertEquals(WebhookSource.INTERNAL, WebhookSource.fromPathValue("INTERNAL"));
        assertEquals(WebhookSource.INTERNAL, WebhookSource.fromPathValue("  internal  "));
    }

    @Test
    void shouldKeepExistingFubResolutionAndUnknownBehavior() {
        assertEquals(WebhookSource.FUB, WebhookSource.fromPathValue("fub"));
        assertNull(WebhookSource.fromPathValue("unknown"));
        assertNull(WebhookSource.fromPathValue(null));
    }
}
