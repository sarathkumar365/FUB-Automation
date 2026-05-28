package com.fuba.automation_engine.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Single-call aggregation for the person detail surface.
 *
 * <p>The UI renders Hero + Timeline + per-stream tabs + Raw snapshot from
 * this payload alone, with one optional live-refresh round-trip to FUB.
 *
 * <ul>
 *   <li>{@code person} — local canonical row.
 *   <li>{@code livePerson} — fresh FUB person payload when {@code includeLive=true}
 *       and the call succeeded. {@code null} otherwise.
 *   <li>{@code liveStatus} — LIVE_OK / LIVE_FAILED / LIVE_SKIPPED. Always populated.
 *   <li>{@code liveMessage} — human-readable detail when LIVE_FAILED; null otherwise.
 *   <li>{@code activity} — top-20 unified chronological timeline across calls / runs / webhooks.
 *   <li>{@code recentCalls / recentWorkflowRuns / recentWebhookEvents} — per-stream
 *       top-10 for the filter-chip views. Subset of {@code activity} but re-shaped
 *       with per-kind detail fields.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonSummaryResponse(
        PersonFeedItemResponse person,
        JsonNode livePerson,
        PersonLiveStatus liveStatus,
        String liveMessage,
        List<PersonActivityEventResponse> activity,
        List<PersonRecentCallResponse> recentCalls,
        List<PersonRecentWorkflowRunResponse> recentWorkflowRuns,
        List<PersonRecentWebhookEventResponse> recentWebhookEvents) {
}
