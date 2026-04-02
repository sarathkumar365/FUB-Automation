package com.fuba.automation_engine.persistence.entity;

public enum PolicyExecutionStepStatus {
    PENDING,
    WAITING_DEPENDENCY,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}
