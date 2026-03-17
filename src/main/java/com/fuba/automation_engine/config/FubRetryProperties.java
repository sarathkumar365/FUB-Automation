package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fub.retry")
public class FubRetryProperties {

    private int maxAttempts = 3;
    private long initialDelayMs = 500;
    private long maxDelayMs = 5_000;
    private double multiplier = 2.0;
    private double jitterFactor = 0.2;
}
