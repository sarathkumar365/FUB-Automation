package com.flux.service.workflow;

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
