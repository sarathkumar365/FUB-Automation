package com.fuba.automation_engine.service.workflow;

import java.util.Map;

public record RetryPolicy(
        int maxAttempts,
        long initialBackoffMs,
        double backoffMultiplier,
        long maxBackoffMs,
        boolean retryOnTransient) {

    public static final RetryPolicy NO_RETRY = new RetryPolicy(1, 0, 1.0, 0, false);
    public static final RetryPolicy DEFAULT_FUB = new RetryPolicy(3, 500, 2.0, 5000, true);

    public static RetryPolicy fromMap(Map<String, Object> map, RetryPolicy fallback) {
        RetryPolicy defaultPolicy = fallback != null ? fallback : NO_RETRY;
        if (map == null || map.isEmpty()) {
            return defaultPolicy;
        }

        int maxAttempts = parseInt(map.get("maxAttempts"), defaultPolicy.maxAttempts());
        long initialBackoffMs = parseLong(map.get("initialBackoffMs"), defaultPolicy.initialBackoffMs());
        double backoffMultiplier = parseDouble(map.get("backoffMultiplier"), defaultPolicy.backoffMultiplier());
        long maxBackoffMs = parseLong(map.get("maxBackoffMs"), defaultPolicy.maxBackoffMs());
        boolean retryOnTransient = parseBoolean(map.get("retryOnTransient"), defaultPolicy.retryOnTransient());

        if (maxAttempts < 1) {
            maxAttempts = defaultPolicy.maxAttempts();
        }
        if (initialBackoffMs < 0) {
            initialBackoffMs = defaultPolicy.initialBackoffMs();
        }
        if (backoffMultiplier <= 0) {
            backoffMultiplier = defaultPolicy.backoffMultiplier();
        }
        if (maxBackoffMs < 0) {
            maxBackoffMs = defaultPolicy.maxBackoffMs();
        }

        return new RetryPolicy(maxAttempts, initialBackoffMs, backoffMultiplier, maxBackoffMs, retryOnTransient);
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long parseLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
            return fallback;
        }
        return fallback;
    }
}
