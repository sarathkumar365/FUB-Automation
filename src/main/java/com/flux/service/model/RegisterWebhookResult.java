package com.flux.service.model;

public record RegisterWebhookResult(Long id, String event, String url, String status) {
}

