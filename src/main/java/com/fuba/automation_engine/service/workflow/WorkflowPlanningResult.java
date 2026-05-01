package com.fuba.automation_engine.service.workflow;

public record WorkflowPlanningResult(
        PlanningStatus status,
        Long runId,
        String reasonCode) {

    public enum PlanningStatus {
        PLANNED,
        DUPLICATE_IGNORED,
        BLOCKED,
        FAILED
    }
}
