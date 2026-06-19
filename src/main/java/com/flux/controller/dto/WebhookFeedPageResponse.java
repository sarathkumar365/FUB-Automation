package com.flux.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record WebhookFeedPageResponse(
        List<WebhookFeedItemResponse> items,
        String nextCursor,
        OffsetDateTime serverTime) {
}
