package com.fuba.automation_engine.service.model;

public record RegisterWebhookResult(Long id, String event, String url, String status) {
}

