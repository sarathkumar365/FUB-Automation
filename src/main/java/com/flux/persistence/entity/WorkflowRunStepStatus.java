package com.flux.persistence.entity;

public enum WorkflowRunStepStatus {
    PENDING,
    WAITING_DEPENDENCY,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}
