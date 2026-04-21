package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai-call-service")
public class AiCallServiceProperties {

    private String baseUrl = "";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private String localSafeToNumber = "";
}
