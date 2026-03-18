package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.exception.webhook.InvalidWebhookFeedQueryException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookFeedCursorCodecTest {

    @Test
    void shouldRejectCursorWithNonNumericId() {
        WebhookFeedCursorCodec codec = new WebhookFeedCursorCodec(new ObjectMapper());
        String rawCursor = "{\"receivedAt\":\"2026-03-17T19:00:00Z\",\"id\":\"abc\"}";
        String encodedCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));

        assertThrows(InvalidWebhookFeedQueryException.class, () -> codec.decode(encodedCursor));
    }
}
