package com.fuba.automation_engine.service.workflow;

import java.util.Map;
import java.util.Set;

public interface WorkflowStepType {

    String id();

    String displayName();

    String description();

    /**
     * Metadata contract for this step's config.
     * Used by admin step catalog and graph validation (currently required-key presence).
     * Runtime value/type/range checks still belong in execute().
     */
    Map<String, Object> configSchema();

    Set<String> declaredResultCodes();

    default RetryPolicy defaultRetryPolicy() {
        return RetryPolicy.NO_RETRY;
    }

    StepExecutionResult execute(StepExecutionContext context);
}
