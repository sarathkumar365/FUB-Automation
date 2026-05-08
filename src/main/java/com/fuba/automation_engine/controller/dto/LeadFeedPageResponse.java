package com.fuba.automation_engine.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cursor-paginated list response. {@code nextCursor} is null when there are
 * no more pages. {@code serverTime} is the server clock at response time,
 * so the client can display "as of N seconds ago" without trusting the
 * local clock.
 */
public record LeadFeedPageResponse(
        List<LeadFeedItemResponse> items,
        String nextCursor,
        OffsetDateTime serverTime) {
}
