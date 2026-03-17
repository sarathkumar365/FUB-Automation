package com.fuba.automation_engine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rules.call-outcome")
public class CallOutcomeRulesProperties {

    private int shortCallThresholdSeconds = 30;
    private int taskDueInDays = 1;
    private Long devTestUserId = 0L;
}
