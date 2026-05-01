package com.fuba.automation_engine.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the role / route matrix from
 * {@code Docs/features/dev-hosting-security-hardening/plan.md} § A1.6.
 *
 * <p>Webhooks and health stay open; admin endpoints require auth; ADMIN-only
 * mutations 403 for OPERATOR/VIEWER.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void healthIsAnonymous() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void webhooksDoNotRequireAuth() throws Exception {
        // No Authorization header. /webhooks/* is permitAll, so any 401 must come from
        // the application-level signature check (handled by WebhookIngressController),
        // not from Spring Security's JsonAuthEntryPoint.
        mockMvc.perform(post("/webhooks/fub")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("\"error\":\"unauthorized\"")) {
                        throw new AssertionError(
                                "Expected /webhooks/fub to be permitAll, but Spring Security 401'd it");
                    }
                });
    }

    @Test
    @WithAnonymousUser
    void adminListLeadsAnonymousReturns401() throws Exception {
        mockMvc.perform(get("/admin/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanGetLeads() throws Exception {
        mockMvc.perform(get("/admin/leads")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotActivateWorkflow() throws Exception {
        mockMvc.perform(post("/admin/workflows/anything/activate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotActivateWorkflow() throws Exception {
        mockMvc.perform(post("/admin/workflows/anything/activate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachActivateEndpoint() throws Exception {
        // Workflow doesn't exist; expect 4xx but NOT 401/403.
        mockMvc.perform(post("/admin/workflows/missing-key/activate"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("ADMIN should reach /activate; got " + status);
                    }
                });
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotCancelRun() throws Exception {
        mockMvc.perform(post("/admin/workflow-runs/1/cancel"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanReachCancelRun() throws Exception {
        // Run doesn't exist; expect 4xx but NOT 401/403.
        mockMvc.perform(post("/admin/workflow-runs/9999/cancel"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("OPERATOR should reach /cancel; got " + status);
                    }
                });
    }
}
