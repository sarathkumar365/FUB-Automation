package com.fuba.automation_engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FubWebhookSyncScriptTest {

    @Test
    void shouldDefineExpectedHostedWebhookSyncContract() throws Exception {
        Path scriptPath = Path.of("scripts", "fub-webhook-sync.sh");
        assertTrue(Files.exists(scriptPath), "scripts/fub-webhook-sync.sh should exist");

        String content = Files.readString(scriptPath);

        assertTrue(content.contains("PUBLIC_BASE_URL"), "script should require hosted base URL env var");
        assertTrue(content.contains("--dry-run"), "script should support a dry-run mode");
        assertTrue(content.contains("config/fub-webhook-events.txt"), "script should use managed events config file");
        assertTrue(content.contains("https://api.followupboss.com/v1/webhooks"),
                "script should target Follow Up Boss webhooks endpoint");
        assertTrue(content.contains("curl -sS --fail -X POST \"${FUB_WEBHOOKS_ENDPOINT}\""),
                "script should create missing webhooks");
        assertTrue(content.contains("curl -sS --fail -X PUT \"${FUB_WEBHOOKS_ENDPOINT}/${webhook_id}\""),
                "script should update existing webhooks");
        assertTrue(content.contains("/webhooks/fub"),
                "script should sync webhook target to hosted /webhooks/fub endpoint");
    }
}
