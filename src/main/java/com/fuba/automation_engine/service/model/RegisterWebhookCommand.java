package com.fuba.automation_engine.service.model;

public record RegisterWebhookCommand(String event, String url) {
}

