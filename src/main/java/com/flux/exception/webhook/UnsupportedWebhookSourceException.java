package com.flux.exception.webhook;

public class UnsupportedWebhookSourceException extends RuntimeException {

    public UnsupportedWebhookSourceException(String message) {
        super(message);
    }
}
