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

    /**
     * Master kill switch for the hardcoded call → task automation. Default
     * {@code true} to preserve existing local-dev and test behaviour; the
     * deployed {@code prod} profile flips it to {@code false} so no real FUB
     * tasks are created. The legacy automation is being retired in favour of
     * the workflow engine; this flag lets us stop the action without yet
     * deleting the decision pipeline.
     */
    private boolean taskCreationEnabled = true;
}
