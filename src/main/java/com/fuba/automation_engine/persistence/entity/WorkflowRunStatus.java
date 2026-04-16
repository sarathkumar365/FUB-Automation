package com.fuba.automation_engine.persistence.entity;

public enum WorkflowRunStatus {
    PENDING,
    BLOCKED,
    DUPLICATE_IGNORED,
    CANCELED,
    COMPLETED,
    FAILED
}
