package com.fuba.automation_engine.service.workflow.aicall;

import lombok.Getter;

@Getter
public class AiCallServiceClientException extends RuntimeException {

    private final boolean transientFailure;
    private final Integer statusCode;

    public AiCallServiceClientException(String message, boolean transientFailure, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
        this.statusCode = statusCode;
    }

    public AiCallServiceClientException(String message, boolean transientFailure, Integer statusCode) {
        super(message);
        this.transientFailure = transientFailure;
        this.statusCode = statusCode;
    }
}
