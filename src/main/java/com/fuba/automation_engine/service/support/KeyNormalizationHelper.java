package com.fuba.automation_engine.service.support;

import java.util.Locale;

public final class KeyNormalizationHelper {

    public static final int POLICY_DOMAIN_MAX_LENGTH = 64;
    public static final int POLICY_KEY_MAX_LENGTH = 128;

    private KeyNormalizationHelper() {
    }

    public static String normalizeWorkflowKey(String workflowKey) {
        if (workflowKey == null) {
            return null;
        }
        String normalized = workflowKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeWorkflowKeyOrEmpty(String workflowKey) {
        String normalized = normalizeWorkflowKey(workflowKey);
        return normalized == null ? "" : normalized;
    }

    public static String normalizeUpperToken(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeUpperToken(String token, int maxLength) {
        String normalized = normalizeUpperToken(token);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return null;
        }
        return normalized;
    }

    public static String normalizeUpperTokenOrEmpty(String token) {
        String normalized = normalizeUpperToken(token);
        return normalized == null ? "" : normalized;
    }

    public static String normalizePolicyDomain(String domain) {
        return normalizeUpperToken(domain, POLICY_DOMAIN_MAX_LENGTH);
    }

    public static String normalizePolicyDomainOrEmpty(String domain) {
        String normalized = normalizePolicyDomain(domain);
        return normalized == null ? "" : normalized;
    }

    public static String normalizePolicyKey(String policyKey) {
        return normalizeUpperToken(policyKey, POLICY_KEY_MAX_LENGTH);
    }

    public static String normalizePolicyKeyOrEmpty(String policyKey) {
        String normalized = normalizePolicyKey(policyKey);
        return normalized == null ? "" : normalized;
    }
}
