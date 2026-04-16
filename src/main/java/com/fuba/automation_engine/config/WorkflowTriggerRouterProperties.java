package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "workflow.trigger-router")
public class WorkflowTriggerRouterProperties {

    private int maxFanoutPerEvent = 200;
}
