package com.fuba.automation_engine;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigTest {

    @Test
    void shouldConfigureDevFileLoggingInLogbackSpring() throws Exception {
        Path configPath = Path.of("src", "main", "resources", "logback-spring.xml");
        assertTrue(Files.exists(configPath), "logback-spring.xml should exist");

        String content = Files.readString(configPath);
        assertTrue(content.contains("springProfile name=\"local\""), "local profile block should exist");
        assertTrue(content.contains("name=\"FILE\""), "file appender should exist for dev profile");
        assertTrue(content.contains("${APP_LOG_FILE"), "file appender should use APP_LOG_FILE");
        assertTrue(content.contains("springProfile name=\"!local\""), "non-local profile block should exist");
    }
}
