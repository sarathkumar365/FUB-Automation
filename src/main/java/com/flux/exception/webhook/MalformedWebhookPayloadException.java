package com.flux.exception.webhook;

public class MalformedWebhookPayloadException extends RuntimeException {

    public MalformedWebhookPayloadException(String message) {
        super(message);
    }
}
