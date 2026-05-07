package com.fuba.automation_engine.service.webhook.parse;

import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

/**
 * Multi-CRM extension seam.
 *
 * Each CRM (Follow Up Boss, HubSpot, Salesforce, Pipedrive, ...) has its own webhook
 * vocabulary on the wire (e.g. FUB sends "peopleUpdated", HubSpot sends "contact.propertyChange").
 * Parsers translate that source-specific dialect into our CRM-agnostic normalized model
 * ({@link NormalizedWebhookEvent} carrying a {@link com.fuba.automation_engine.service.webhook.model.NormalizedDomain}
 * and {@link com.fuba.automation_engine.service.webhook.model.NormalizedAction}).
 *
 * To add a new CRM:
 *   1. Add a value to {@link WebhookSource}.
 *   2. Implement this interface as a Spring bean; have {@code supports(source)} match the new value.
 *   3. Map the CRM's event types onto the existing normalized domains/actions
 *      (LEAD, CALL, ...). Add new normalized values only if no existing one fits.
 *
 * Downstream code (workflow triggers, the feed UI, the expression scope) is CRM-agnostic
 * and depends only on the normalized model. Workflow JSON references domains/actions —
 * never CRM-specific event types — so workflows port across CRMs without changes.
 */
public interface WebhookParser {

    boolean supports(WebhookSource source);

    NormalizedWebhookEvent parse(String rawBody, Map<String, String> headers);
}
