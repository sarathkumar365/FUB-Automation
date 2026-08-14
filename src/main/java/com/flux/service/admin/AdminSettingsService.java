package com.flux.service.admin;

import com.flux.config.BusinessHoursProperties;
import com.flux.config.CallOutcomeRulesProperties;
import com.flux.config.FubClientProperties;
import com.flux.config.FubRetryProperties;
import com.flux.config.WebhookProperties;
import com.flux.controller.dto.SettingsConfigResponse;
import com.flux.controller.dto.SettingsConfigResponse.BusinessHoursSection;
import com.flux.controller.dto.SettingsConfigResponse.CallRulesSection;
import com.flux.controller.dto.SettingsConfigResponse.FubConnectionSection;
import com.flux.controller.dto.SettingsConfigResponse.FubRetrySection;
import com.flux.controller.dto.SettingsConfigResponse.Redactable;
import com.flux.controller.dto.SettingsConfigResponse.WebhookSection;
import org.springframework.stereotype.Service;

/**
 * Aggregates the {@code @ConfigurationProperties} beans into a single
 * operator-facing response. Sensitive fields are redacted to {@code "***"}.
 */
@Service
public class AdminSettingsService {

    private final BusinessHoursProperties businessHours;
    private final FubClientProperties fubClient;
    private final FubRetryProperties fubRetry;
    private final WebhookProperties webhook;
    private final CallOutcomeRulesProperties callRules;

    public AdminSettingsService(
            BusinessHoursProperties businessHours,
            FubClientProperties fubClient,
            FubRetryProperties fubRetry,
            WebhookProperties webhook,
            CallOutcomeRulesProperties callRules) {
        this.businessHours = businessHours;
        this.fubClient = fubClient;
        this.fubRetry = fubRetry;
        this.webhook = webhook;
        this.callRules = callRules;
    }

    public SettingsConfigResponse currentConfig() {
        return new SettingsConfigResponse(
                new BusinessHoursSection(
                        businessHours.getTimezone(),
                        businessHours.getStartHour(),
                        businessHours.getEndHour(),
                        businessHours.isWeekdaysOnly()),
                new FubConnectionSection(
                        fubClient.getBaseUrl(),
                        Redactable.from(fubClient.getApiKey()),
                        fubClient.getXSystem(),
                        Redactable.from(fubClient.getXSystemKey())),
                new FubRetrySection(
                        fubRetry.getMaxAttempts(),
                        fubRetry.getInitialDelayMs(),
                        fubRetry.getMaxDelayMs(),
                        fubRetry.getMultiplier(),
                        fubRetry.getJitterFactor()),
                new WebhookSection(
                        webhook.getMaxBodyBytes(),
                        new WebhookSection.FubSourceSection(
                                webhook.getSources().getFub().isEnabled(),
                                Redactable.from(webhook.getSources().getFub().getSigningKey())),
                        webhook.getLiveFeed().getHeartbeatSeconds(),
                        webhook.getLiveFeed().getEmitterTimeoutMs()),
                new CallRulesSection(
                        callRules.getShortCallThresholdSeconds(),
                        callRules.getTaskDueInDays(),
                        callRules.isTaskCreationEnabled()));
    }
}
