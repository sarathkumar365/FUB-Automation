package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * One event in the unified lead activity timeline. {@code occurredAt} is the
 * canonical "when it happened" timestamp per kind:
 *
 * <ul>
 *   <li>PROCESSED_CALL → {@code call_started_at}
 *   <li>WORKFLOW_RUN → {@code created_at}
 *   <li>WEBHOOK_EVENT → {@code received_at}
 * </ul>
 *
 * {@code refId} is the database id of the source row (scoped to kind), which
 * the UI uses to deep-link to the source detail surface (call/run/webhook).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadActivityEventResponse(
        LeadActivityKind kind,
        Long refId,
        OffsetDateTime occurredAt,
        String summary,
        String status) {
}
