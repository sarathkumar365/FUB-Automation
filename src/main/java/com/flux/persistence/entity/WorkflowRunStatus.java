package com.flux.persistence.entity;

public enum WorkflowRunStatus {
    PENDING,
    BLOCKED,
    DUPLICATE_IGNORED,
    CANCELED,
    COMPLETED,
    FAILED
}
