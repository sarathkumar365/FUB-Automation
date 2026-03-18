package com.fuba.automation_engine.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.repository.WebhookEventRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedReadQuery;
import com.fuba.automation_engine.service.webhook.AdminWebhookService.WebhookFeedQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminWebhookStaleQueryGuardTest {

    @Test
    void shouldReadFeedOnlyThroughJdbcFeedRepository() {
        WebhookFeedReadRepository feedReadRepository = Mockito.mock(WebhookFeedReadRepository.class);
        WebhookEventRepository webhookEventRepository = Mockito.mock(WebhookEventRepository.class);
        WebhookFeedCursorCodec cursorCodec = new WebhookFeedCursorCodec(new ObjectMapper());
        AdminWebhookService service = new AdminWebhookService(
                feedReadRepository,
                webhookEventRepository,
                cursorCodec,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC));

        Mockito.when(feedReadRepository.fetch(Mockito.any(WebhookFeedReadQuery.class))).thenReturn(List.of());

        service.list(new WebhookFeedQuery(null, null, null, null, null, 10, null, false));

        Mockito.verify(feedReadRepository, Mockito.times(1)).fetch(Mockito.any(WebhookFeedReadQuery.class));
        Mockito.verifyNoInteractions(webhookEventRepository);
    }

    @Test
    void shouldReadDetailThroughWebhookEventRepository() {
        WebhookFeedReadRepository feedReadRepository = Mockito.mock(WebhookFeedReadRepository.class);
        WebhookEventRepository webhookEventRepository = Mockito.mock(WebhookEventRepository.class);
        WebhookFeedCursorCodec cursorCodec = new WebhookFeedCursorCodec(new ObjectMapper());
        AdminWebhookService service = new AdminWebhookService(
                feedReadRepository,
                webhookEventRepository,
                cursorCodec,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC));

        Mockito.when(webhookEventRepository.findById(123L)).thenReturn(Optional.empty());

        service.findDetail(123L);

        Mockito.verify(webhookEventRepository, Mockito.times(1)).findById(123L);
        Mockito.verifyNoInteractions(feedReadRepository);
    }
}
