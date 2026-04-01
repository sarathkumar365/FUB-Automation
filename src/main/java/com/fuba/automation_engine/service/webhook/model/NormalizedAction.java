package com.fuba.automation_engine.service.webhook.model;

/**
 * Source-agnostic action semantics used by lead-management routing.
 *
 * <p>CREATED: A new lead-domain record/event was created (for example, a new person/lead appears).
 * UPDATED: An existing lead-domain record/event changed (for example, lead attributes or ownership details changed).
 * ASSIGNED: Lead ownership was explicitly assigned/reassigned to a user or team.
 * UNKNOWN: Action could not be safely mapped from source payload and must be treated as non-specific.
 */
public enum NormalizedAction {
    CREATED,
    UPDATED,
    ASSIGNED,
    UNKNOWN
}
