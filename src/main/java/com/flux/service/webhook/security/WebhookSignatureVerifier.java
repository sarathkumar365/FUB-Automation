package com.flux.service.webhook.security;

import com.flux.service.webhook.model.WebhookSource;
import java.util.Map;

public interface WebhookSignatureVerifier {

    boolean supports(WebhookSource source);

    boolean verify(String rawBody, Map<String, String> headers);
}
