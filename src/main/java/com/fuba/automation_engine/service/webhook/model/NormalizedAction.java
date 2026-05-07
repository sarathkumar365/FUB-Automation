package com.fuba.automation_engine.service.webhook.model;

/**
 * CRM-agnostic action verb paired with a {@link NormalizedDomain}.
 *
 * <ul>
 *   <li>{@code CREATED} — a new record appeared.</li>
 *   <li>{@code UPDATED} — an existing record changed. Specific changes (assignment, stage, tags)
 *       are detected via field-diff inside the workflow rather than encoded as separate actions.</li>
 *   <li>{@code UNKNOWN} — fallback when an action can't be safely mapped.</li>
 * </ul>
 */
public enum NormalizedAction {
    CREATED,
    UPDATED,
    UNKNOWN
}
