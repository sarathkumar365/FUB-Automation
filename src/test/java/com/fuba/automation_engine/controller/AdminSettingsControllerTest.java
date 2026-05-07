package com.fuba.automation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void shouldReturnConfigurationSnapshotForAdmin() throws Exception {
        mockMvc.perform(get("/admin/settings/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessHours.timezone").value("America/Toronto"))
                .andExpect(jsonPath("$.businessHours.startHour").value(9))
                .andExpect(jsonPath("$.businessHours.endHour").value(18))
                .andExpect(jsonPath("$.businessHours.weekdaysOnly").value(true))
                .andExpect(jsonPath("$.fubRetry.maxAttempts").exists())
                .andExpect(jsonPath("$.callRules.shortCallThresholdSeconds").exists())
                .andExpect(jsonPath("$.webhook.maxBodyBytes").exists())
                .andExpect(jsonPath("$.webhook.sources.enabled").exists())
                .andExpect(jsonPath("$.webhook.sources.signingKey.value").value(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.nullValue(),
                                org.hamcrest.Matchers.equalTo("***"))));
    }

    @Test
    @WithMockUser(username = "viewer-test", roles = "VIEWER")
    void shouldAllowViewerRoleToReadConfig() throws Exception {
        mockMvc.perform(get("/admin/settings/config"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/admin/settings/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin-test", roles = "ADMIN")
    void shouldRedactSensitiveFieldsAndExposePresenceOnly() throws Exception {
        // The test profile sets fub.api-key, fub.x-system-key, and signing-key
        // to either real test values or empty strings via env vars. Whatever
        // value is configured must NEVER appear in the response — only the
        // redaction sentinel "***" or null when absent.
        String body = mockMvc.perform(get("/admin/settings/config"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // The presence flag is informative; the value field is "***" or null.
        // This test asserts the value field never leaks an actual key — any
        // raw value that looks like an FUB API key has the prefix "fka_" and
        // similarly system keys are 32-char hex. If the test environment has
        // those, they would only appear if redaction is broken.
        org.assertj.core.api.Assertions.assertThat(body).doesNotMatch("(?s).*\"value\"\\s*:\\s*\"fka_[A-Za-z0-9]+\".*");
        // Verify shape: redactable fields have present + value keys.
        mockMvc.perform(get("/admin/settings/config"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"present\"")))
                .andExpect(jsonPath("$.fubConnection.apiKey.value").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.nullValue(), org.hamcrest.Matchers.equalTo("***"))));
    }
}
