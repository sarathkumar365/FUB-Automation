package com.fuba.automation_engine.exception.fub;

import lombok.Getter;

@Getter
public class FubTransientException extends RuntimeException {

    private final Integer statusCode;

    public FubTransientException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public FubTransientException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}

