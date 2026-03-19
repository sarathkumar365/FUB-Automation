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
        assertTrue(content.contains("APP_LOG_FILE"), "script should pass APP_LOG_FILE to Spring Boot in dev mode");
        assertTrue(content.contains("UI_LOG_FILE"), "script should define a frontend log file");
        assertTrue(content.contains("mkdir -p"), "script should create the log directory in dev mode");
        assertTrue(content.contains(": > \"${APP_LOG_FILE}\""), "script should clear backend log file on each dev start");
        assertTrue(content.contains(": > \"${UI_LOG_FILE}\""), "script should clear frontend log file on each dev start");
        assertTrue(content.contains("run dev --prefix"), "script should start the Vite dev server in dev mode");
        assertTrue(content.contains("UI_PID"), "script should track frontend process id for cleanup");
        assertTrue(content.contains("Frontend ready at"), "script should log a frontend-ready message after startup check");
        assertTrue(content.contains("STARTUP_LOG_FILE"), "script should define a startup log file");
        assertTrue(content.contains(": > \"${STARTUP_LOG_FILE}\""), "script should clear startup log file on each start");
        assertTrue(content.contains("Cloudflare tunnel URL"), "script should log the generated tunnel URL");
    }
}
