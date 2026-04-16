package com.fuba.automation_engine.service.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyNormalizationHelperTest {

    @Test
    void shouldNormalizeWorkflowKeyByTrimmingOnly() {
        assertEquals("Workflow_Key", KeyNormalizationHelper.normalizeWorkflowKey("  Workflow_Key  "));
        assertEquals("workflow_key", KeyNormalizationHelper.normalizeWorkflowKey("workflow_key"));
    }

    @Test
    void shouldReturnNullForBlankWorkflowKey() {
        assertNull(KeyNormalizationHelper.normalizeWorkflowKey(null));
        assertNull(KeyNormalizationHelper.normalizeWorkflowKey(" "));
    }

    @Test
    void shouldNormalizePolicyTokensToUppercase() {
        assertEquals("ASSIGNMENT", KeyNormalizationHelper.normalizePolicyDomain(" assignment "));
        assertEquals("FOLLOW_UP_SLA", KeyNormalizationHelper.normalizePolicyKey(" follow_up_sla "));
    }

    @Test
    void shouldRejectPolicyTokenThatExceedsMaxLength() {
        assertNull(KeyNormalizationHelper.normalizePolicyDomain("A".repeat(65)));
        assertNull(KeyNormalizationHelper.normalizePolicyKey("B".repeat(129)));
    }

    @Test
    void shouldReturnEmptyForOrEmptyHelpersWhenInputInvalid() {
        assertEquals("", KeyNormalizationHelper.normalizeWorkflowKeyOrEmpty(" "));
        assertEquals("", KeyNormalizationHelper.normalizePolicyDomainOrEmpty(" "));
        assertEquals("", KeyNormalizationHelper.normalizePolicyKeyOrEmpty(null));
    }
}
