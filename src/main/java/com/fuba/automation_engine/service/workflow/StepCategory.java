package com.fuba.automation_engine.service.workflow;

public enum StepCategory {
    /** Engine primitive shaping execution (delay, branch, loop). */
    CONTROL,
    /** Generic, domain-neutral action usable by any deployment. */
    UTILITY,
    /** Domain/vendor-specific action tied to this host application. */
    BUSINESS
}
