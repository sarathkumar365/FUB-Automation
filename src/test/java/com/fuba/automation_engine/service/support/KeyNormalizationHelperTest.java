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
    void shouldReturnEmptyForOrEmptyHelpersWhenInputInvalid() {
        assertEquals("", KeyNormalizationHelper.normalizeWorkflowKeyOrEmpty(" "));
    }
}
