package com.fuba.automation_engine.service.support;

public final class KeyNormalizationHelper {

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
}
