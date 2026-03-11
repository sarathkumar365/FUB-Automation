package com.fuba.automation_engine.exception.fub;

import lombok.Getter;

@Getter
public class FubPermanentException extends RuntimeException {

    private final Integer statusCode;

    public FubPermanentException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public FubPermanentException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}

