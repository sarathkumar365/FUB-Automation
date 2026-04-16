package com.fuba.automation_engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookReactivationScriptTest {

    @Test
    void shouldDefineExpectedFubWebhookReactivationContract() throws Exception {
        Path scriptPath = Path.of("scripts", "fub-webhook-reactivate.sh");
        assertTrue(Files.exists(scriptPath), "scripts/fub-webhook-reactivate.sh should exist");

        String content = Files.readString(scriptPath);

        assertTrue(content.contains("https://api.followupboss.com/v1/webhooks"),
                "script should target Follow Up Boss webhook endpoints");
        assertTrue(content.contains("curl -sS --fail \"${FUB_WEBHOOKS_ENDPOINT}\""),
                "script should fetch webhook list with GET");
        assertTrue(content.contains("curl -sS --fail -X PUT \"${FUB_WEBHOOKS_ENDPOINT}/${webhook_id}\""),
                "script should update webhook status via PUT /v1/webhooks/{id}");
        assertTrue(content.contains("{\"status\":\"Active\"}"),
                "script should send Active status payload");
        assertTrue(content.contains("X-System: ${FUB_X_SYSTEM}"),
                "script should include X-System header");
        assertTrue(content.contains("X-System-Key: ${FUB_X_SYSTEM_KEY}"),
                "script should include X-System-Key header");
        assertTrue(content.contains("config/fub-webhook-events.txt"),
                "script should use managed events config file");
    }
}
