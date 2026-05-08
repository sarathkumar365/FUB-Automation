package com.fuba.automation_engine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WorkflowWorkerProperties.class)
public class WorkflowWorkerSchedulingConfig {
}
