package com.flux.service.workflow.cortex;

import lombok.Getter;

@Getter
public class CortexClientException extends RuntimeException {

    private final boolean transientFailure;
    private final Integer statusCode;

    public CortexClientException(String message, boolean transientFailure, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.transientFailure = transientFailure;
        this.statusCode = statusCode;
    }

    public CortexClientException(String message, boolean transientFailure, Integer statusCode) {
        super(message);
        this.transientFailure = transientFailure;
        this.statusCode = statusCode;
    }
}
