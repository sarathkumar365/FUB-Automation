package com.fuba.automation_engine.service.workflow.http;

import lombok.Getter;

@Getter
public class WorkflowHttpClientException extends RuntimeException {

    private final boolean transientFailure;

    public WorkflowHttpClientException(String message, boolean transientFailure, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public WorkflowHttpClientException(String message, boolean transientFailure) {
        super(message);
        this.transientFailure = transientFailure;
    }
}

