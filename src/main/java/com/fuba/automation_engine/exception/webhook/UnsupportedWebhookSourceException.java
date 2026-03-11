package com.fuba.automation_engine.exception.webhook;

public class UnsupportedWebhookSourceException extends RuntimeException {

    public UnsupportedWebhookSourceException(String message) {
        super(message);
    }
}
