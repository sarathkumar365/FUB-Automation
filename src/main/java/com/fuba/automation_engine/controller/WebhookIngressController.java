package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.exception.webhook.InvalidWebhookSignatureException;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.exception.webhook.UnsupportedWebhookSourceException;
import com.fuba.automation_engine.service.webhook.WebhookIngressService;
import com.fuba.automation_engine.service.webhook.model.WebhookIngressResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class WebhookIngressController {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngressController.class);

    private final WebhookIngressService webhookIngressService;

    public WebhookIngressController(WebhookIngressService webhookIngressService) {
        this.webhookIngressService = webhookIngressService;
    }

    @PostMapping(path = "/{source}", consumes = "application/json")
    public ResponseEntity<WebhookIngressResult> receiveWebhook(
            @PathVariable String source,
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers) {

        log.info(
                "Webhook request received source={} contentLength={} headerCount={}",
                source,
                rawBody == null ? 0 : rawBody.length(),
                headers.size());
        WebhookIngressResult result = webhookIngressService.ingest(source, rawBody, flattenHeaders(headers));
        log.info("Webhook request accepted source={} message={}", source, result.message());
        return ResponseEntity.accepted().body(result);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<String> handleInvalidSignature(InvalidWebhookSignatureException ex) {
        log.warn("Webhook signature validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler({MalformedWebhookPayloadException.class, UnsupportedWebhookSourceException.class})
    public ResponseEntity<String> handleBadRequest(RuntimeException ex) {
        log.warn("Webhook request rejected: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    private Map<String, String> flattenHeaders(HttpHeaders headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                flattened.put(name, values.get(0));
            }
        });
        return flattened;
    }
}
