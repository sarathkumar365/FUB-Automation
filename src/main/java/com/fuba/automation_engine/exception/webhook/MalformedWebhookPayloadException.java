package com.fuba.automation_engine.exception.webhook;

public class MalformedWebhookPayloadException extends RuntimeException {

    public MalformedWebhookPayloadException(String message) {
        super(message);
    }
}
