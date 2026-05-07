package com.fuba.automation_engine.service.webhook.model;

/**
 * CRM-agnostic event domain. Each {@link com.fuba.automation_engine.service.webhook.parse.WebhookParser}
 * implementation translates its source's vocabulary into one of these values.
 *
 * <ul>
 *   <li>{@code LEAD} — events about a prospect/contact record. FUB calls this "person";
 *       HubSpot/GHL call it "contact"; Salesforce splits it into Lead/Contact. We normalize
 *       all of them to LEAD so workflows port across CRMs.</li>
 *   <li>{@code CALL} — phone-call activity.</li>
 *   <li>{@code UNKNOWN} — fallback when an event can't be safely mapped.</li>
 * </ul>
 *
 * Add new values only when no existing one fits (likely candidates as we expand:
 * NOTE, TASK, DEAL, APPOINTMENT, USER).
 */
public enum NormalizedDomain {
    CALL,
    LEAD,
    UNKNOWN
}
