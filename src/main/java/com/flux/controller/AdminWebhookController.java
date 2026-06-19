package com.flux.controller;

import com.flux.controller.dto.WebhookEventDetailResponse;
import com.flux.controller.dto.WebhookFeedPageResponse;
import com.flux.exception.webhook.InvalidWebhookFeedQueryException;
import com.flux.service.webhook.AdminWebhookService;
import com.flux.service.webhook.AdminWebhookService.WebhookFeedQuery;
import com.flux.service.webhook.live.WebhookSseHub;
import com.flux.service.webhook.live.WebhookSseHub.WebhookStreamFilter;
import com.flux.service.webhook.model.WebhookEventStatus;
import com.flux.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/admin/webhooks")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
public class AdminWebhookController {

    private final AdminWebhookService adminWebhookService;
    private final WebhookSseHub webhookSseHub;

    public AdminWebhookController(AdminWebhookService adminWebhookService, WebhookSseHub webhookSseHub) {
        this.adminWebhookService = adminWebhookService;
        this.webhookSseHub = webhookSseHub;
    }

    @GetMapping
    public ResponseEntity<WebhookFeedPageResponse> list(
            @RequestParam(required = false) WebhookSource source,
            @RequestParam(required = false) WebhookEventStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean includePayload) {
        WebhookFeedPageResponse response = adminWebhookService.list(new WebhookFeedQuery(
                source,
                status,
                eventType,
                from,
                to,
                limit,
                cursor,
                includePayload));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/event-types")
    public ResponseEntity<List<String>> listEventTypes() {
        return ResponseEntity.ok(adminWebhookService.listDistinctEventTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookEventDetailResponse> detail(@PathVariable long id) {
        return adminWebhookService.findDetail(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook event not found"));
    }

    @GetMapping(path = "/stream", produces = "text/event-stream")
    public SseEmitter stream(
            @RequestParam(required = false) WebhookSource source,
            @RequestParam(required = false) WebhookEventStatus status,
            @RequestParam(required = false) String eventType) {
        return webhookSseHub.subscribe(new WebhookStreamFilter(source, status, eventType));
    }

    @ExceptionHandler(InvalidWebhookFeedQueryException.class)
    public ResponseEntity<String> handleInvalidFeedQuery(InvalidWebhookFeedQueryException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
