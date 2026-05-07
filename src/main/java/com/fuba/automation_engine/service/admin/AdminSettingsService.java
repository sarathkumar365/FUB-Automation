package com.fuba.automation_engine.service.admin;

import com.fuba.automation_engine.config.BusinessHoursProperties;
import com.fuba.automation_engine.config.CallOutcomeRulesProperties;
import com.fuba.automation_engine.config.FubClientProperties;
import com.fuba.automation_engine.config.FubRetryProperties;
import com.fuba.automation_engine.config.WebhookProperties;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.BusinessHoursSection;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.CallRulesSection;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.FubConnectionSection;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.FubRetrySection;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.Redactable;
import com.fuba.automation_engine.controller.dto.SettingsConfigResponse.WebhookSection;
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
