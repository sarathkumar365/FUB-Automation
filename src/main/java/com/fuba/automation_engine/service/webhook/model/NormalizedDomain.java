package com.fuba.automation_engine.service.webhook.model;

/**
 * CRM-agnostic event domain. Each {@link com.fuba.automation_engine.service.webhook.parse.WebhookParser}
 * implementation translates its source's vocabulary into one of these values.
 *
 * <ul>
 *   <li>{@code PERSON} — events about a person record. FUB calls this "person"
 *       ({@code /v1/people}); HubSpot/GHL call it "contact"; Salesforce splits it into
 *       Person/Contact. We normalize all of them to PERSON so workflows port across CRMs.</li>
 *   <li>{@code CALL} — phone-call activity.</li>
 *   <li>{@code UNKNOWN} — fallback when an event can't be safely mapped.</li>
 * </ul>
 *
 * Add new values only when no existing one fits (likely candidates as we expand:
 * NOTE, TASK, DEAL, APPOINTMENT, USER).
 */
public enum NormalizedDomain {
    CALL,
    PERSON,
    UNKNOWN
}
