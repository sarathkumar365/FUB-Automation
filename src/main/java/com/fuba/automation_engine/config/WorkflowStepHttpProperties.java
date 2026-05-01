package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "workflow.step-http")
public class WorkflowStepHttpProperties {

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}

