package com.fuba.automation_engine.service.workflow;

/**
 * Step-type classification per RD-010. CONTROL and UTILITY steps ship with the
 * extractable engine kernel and must not depend on host/business packages
 * (enforced by StepCategoryBoundaryTest); BUSINESS steps stay in the host app.
 */
public enum StepCategory {
    /** Engine primitive shaping execution (delay, branch, loop). */
    CONTROL,
    /** Generic, domain-neutral action usable by any deployment. */
    UTILITY,
    /** Domain/vendor-specific action tied to this host application. */
    BUSINESS
}
