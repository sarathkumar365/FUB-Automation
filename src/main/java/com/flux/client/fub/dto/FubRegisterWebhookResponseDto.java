package com.flux.client.fub.dto;

public record FubRegisterWebhookResponseDto(Long id, String event, String url, String status) {
}

