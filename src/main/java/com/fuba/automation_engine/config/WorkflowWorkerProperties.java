package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "workflow.worker")
public class WorkflowWorkerProperties {

    private boolean enabled = false;
    private long pollIntervalMs = 2000L;
    private int claimBatchSize = 50;
    private int maxStepsPerPoll = 200;
    private boolean staleProcessingEnabled = true;
    private int staleProcessingTimeoutMinutes = 15;
    private int staleProcessingRequeueLimit = 1;
    private int staleProcessingBatchSize = 50;
}
