package com.fuba.automation_engine.service.webhook.security;

import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

public interface WebhookSignatureVerifier {

    boolean supports(WebhookSource source);

    boolean verify(String rawBody, Map<String, String> headers);
}
