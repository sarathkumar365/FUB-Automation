package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "policy.worker")
public class PolicyWorkerProperties {

    private boolean enabled = true;
    private long pollIntervalMs = 2000L;
    private int claimBatchSize = 50;
    private int maxStepsPerPoll = 200;
}
