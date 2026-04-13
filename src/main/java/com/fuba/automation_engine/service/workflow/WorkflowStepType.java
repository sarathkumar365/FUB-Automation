package com.fuba.automation_engine.service.workflow;

import java.util.Map;
import java.util.Set;

public interface WorkflowStepType {

    String id();

    String displayName();

    String description();

    Map<String, Object> configSchema();

    Set<String> declaredResultCodes();

    default RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.NO_RETRY;
    }

    StepExecutionResult execute(StepExecutionContext context);
}
