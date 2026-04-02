package com.fuba.automation_engine.persistence.entity;

public enum PolicyExecutionRunStatus {
    PENDING,
    BLOCKED_IDENTITY,
    BLOCKED_POLICY,
    DUPLICATE_IGNORED,
    COMPLETED,
    FAILED
}
