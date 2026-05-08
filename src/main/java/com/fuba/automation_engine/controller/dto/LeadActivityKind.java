package com.fuba.automation_engine.controller.dto;

/**
 * Which local domain produced an entry in the unified lead activity timeline.
 * The UI filters the timeline client-side using this discriminator.
 */
public enum LeadActivityKind {
    PROCESSED_CALL,
    WORKFLOW_RUN,
    WEBHOOK_EVENT
}
