package com.fuba.automation_engine.persistence.entity;

public enum WorkflowRunStepStatus {
    PENDING,
    WAITING_DEPENDENCY,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}
