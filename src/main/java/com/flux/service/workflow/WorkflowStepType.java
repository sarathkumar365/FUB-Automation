package com.flux.service.workflow;

import java.util.Map;
import java.util.Set;

public interface WorkflowStepType {

    String id();

    String displayName();

    String description();

    /**
     * Classification per RD-010 — every step type must declare one.
     * CONTROL/UTILITY implementations are kernel code (boundary-tested).
     */
    StepCategory category();

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
