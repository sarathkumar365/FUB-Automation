package com.fuba.automation_engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RunAppScriptTest {

    @Test
    void shouldProvideDevAndProdModesInRunScript() throws Exception {
        Path scriptPath = Path.of("scripts", "run-app.sh");
        assertTrue(Files.exists(scriptPath), "scripts/run-app.sh should exist");

        String content = Files.readString(scriptPath);
        assertTrue(content.contains("dev"), "script should support dev mode");
        assertTrue(content.contains("prod"), "script should support prod mode");
        assertTrue(content.contains("cloudflared"), "script should support cloudflared tunneling");
        assertTrue(content.contains("sync_fub_webhook_url"), "script should include webhook URL sync logic");
    }
}
