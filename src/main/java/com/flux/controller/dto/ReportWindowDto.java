package com.flux.controller.dto;

import java.time.OffsetDateTime;

/**
 * The window a report covers, echoed back so the caller can label and trust the numbers.
 *
 * <p>Its own type rather than a member of one report's DTO: both reports return it, and
 * nesting it inside either would mean changing that report's shape silently changed the
 * other's contract.
 *
 * @param open           true while the window can still change (today / this week)
 * @param eventsReceived webhooks that arrived in the window — lets the UI tell "no leads
 *                       came in" apart from "we heard nothing", which are identical in the
 *                       counts and opposite in meaning
 */
public record ReportWindowDto(
        String key,
        OffsetDateTime from,
        OffsetDateTime to,
        String timezone,
        boolean open,
        long eventsReceived) {}
