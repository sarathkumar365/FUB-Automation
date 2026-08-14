package com.flux.service.model;

public record RegisterWebhookCommand(String event, String url) {
}

