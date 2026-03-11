package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.exception.webhook.InvalidWebhookSignatureException;
import com.fuba.automation_engine.exception.webhook.MalformedWebhookPayloadException;
import com.fuba.automation_engine.exception.webhook.UnsupportedWebhookSourceException;
import com.fuba.automation_engine.service.webhook.WebhookIngressService;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final WebhookIngressService webhookIngressService;

    public WebhookIngressController(WebhookIngressService webhookIngressService) {
        this.webhookIngressService = webhookIngressService;
    }

    @PostMapping(path = "/{source}", consumes = "application/json")
    public ResponseEntity<Void> receiveWebhook(
            @PathVariable String source,
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers) {

        webhookIngressService.ingest(source, rawBody, flattenHeaders(headers));
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<String> handleInvalidSignature(InvalidWebhookSignatureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler({MalformedWebhookPayloadException.class, UnsupportedWebhookSourceException.class})
    public ResponseEntity<String> handleBadRequest(RuntimeException ex) {
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
