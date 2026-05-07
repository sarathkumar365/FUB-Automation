package com.fuba.automation_engine.controller.dto;

/**
 * Read-only operator-facing view of the platform's runtime configuration.
 * Returned by {@code GET /admin/settings/config}.
 *
 * <p>Sensitive fields (API keys, signing keys) are NEVER returned in raw
 * form. {@link Redactable} groups them — {@code present} indicates whether
 * a value is configured; the value itself is replaced with {@code "***"}.
 *
 * <p>This DTO is intentionally a flat snapshot of {@code @ConfigurationProperties}
 * beans. When persistence-backed editable settings land (Phase 7+), this
 * shape can stay; only the source-of-truth changes (DB instead of properties).
 */
public record SettingsConfigResponse(
        BusinessHoursSection businessHours,
        FubConnectionSection fubConnection,
        FubRetrySection fubRetry,
        WebhookSection webhook,
        CallRulesSection callRules) {

    public record BusinessHoursSection(
            String timezone,
            int startHour,
            int endHour,
            boolean weekdaysOnly) {}

    public record FubConnectionSection(
            String baseUrl,
            Redactable apiKey,
            String xSystem,
            Redactable xSystemKey) {}

    public record FubRetrySection(
            int maxAttempts,
            long initialDelayMs,
            long maxDelayMs,
            double multiplier,
            double jitterFactor) {}

    public record WebhookSection(
            int maxBodyBytes,
            FubSourceSection sources,
            int liveFeedHeartbeatSeconds,
            long liveFeedEmitterTimeoutMs) {

        public record FubSourceSection(boolean enabled, Redactable signingKey) {}
    }

    public record CallRulesSection(
            int shortCallThresholdSeconds,
            int taskDueInDays,
            boolean taskCreationEnabled) {}

    /** Redaction wrapper: {@code present=true} when a value is configured. */
    public record Redactable(boolean present, String value) {
        public static Redactable from(String raw) {
            boolean isPresent = raw != null && !raw.isBlank();
            return new Redactable(isPresent, isPresent ? "***" : null);
        }
    }
}
