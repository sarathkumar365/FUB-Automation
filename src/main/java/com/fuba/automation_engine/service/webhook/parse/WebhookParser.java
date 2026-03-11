package com.fuba.automation_engine.service.webhook.parse;

import com.fuba.automation_engine.service.webhook.model.NormalizedWebhookEvent;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.util.Map;

public interface WebhookParser {

    boolean supports(WebhookSource source);

    NormalizedWebhookEvent parse(String rawBody, Map<String, String> headers);
}
