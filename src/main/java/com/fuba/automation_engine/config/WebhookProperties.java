package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    private int maxBodyBytes = 1024 * 1024;
    private Sources sources = new Sources();
    private LiveFeed liveFeed = new LiveFeed();

    @Getter
    @Setter
    public static class Sources {
        private Fub fub = new Fub();
    }

    @Getter
    @Setter
    public static class Fub {
        private boolean enabled = true;
        private String signingKey = "";
    }

    @Getter
    @Setter
    public static class LiveFeed {
        private int heartbeatSeconds = 15;
        private long emitterTimeoutMs = 30L * 60L * 1000L;
    }
}
