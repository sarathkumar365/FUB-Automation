package com.fuba.automation_engine.service.policy;

public interface PolicyStepExecutor {

    boolean supports(PolicyStepType stepType);

    PolicyStepExecutionResult execute(PolicyStepExecutionContext context);
}
