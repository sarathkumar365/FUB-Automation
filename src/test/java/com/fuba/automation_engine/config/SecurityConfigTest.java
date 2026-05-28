package com.fuba.automation_engine.config;

import com.fuba.automation_engine.persistence.entity.AppUserEntity;
import com.fuba.automation_engine.persistence.entity.AppUserRole;
import com.fuba.automation_engine.service.auth.JwtService;
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

    @Autowired
    private JwtService jwtService;

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
    void adminListPersonsAnonymousReturns401() throws Exception {
        mockMvc.perform(get("/admin/persons")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanGetPersons() throws Exception {
        mockMvc.perform(get("/admin/persons")).andExpect(status().isOk());
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

    @Test
    @WithAnonymousUser
    void streamEndpointRejectsTokenInQueryParam() throws Exception {
        // The SPA now uses fetch-event-source which sends the JWT in a header,
        // so the query-param fallback was removed. This test pins the new behaviour:
        // a query-param token must not authenticate, even on the stream endpoint.
        AppUserEntity admin = new AppUserEntity();
        admin.setUsername("admin-test");
        admin.setRole(AppUserRole.ADMIN);
        String token = jwtService.issue(admin).token();

        mockMvc.perform(get("/admin/webhooks/stream?source=FUB&token=" + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void streamEndpointAcceptsTokenInAuthorizationHeader() throws Exception {
        // The SPA's SSE client (fetch-event-source) attaches the JWT in the
        // Authorization header. Verify the header path works on the stream URL.
        AppUserEntity admin = new AppUserEntity();
        admin.setUsername("admin-test");
        admin.setRole(AppUserRole.ADMIN);
        String token = jwtService.issue(admin).token();

        mockMvc.perform(get("/admin/webhooks/stream?source=FUB")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) {
                        throw new AssertionError(
                                "Expected header-bearer auth to pass on /admin/webhooks/stream, got 401");
                    }
                });
    }
}
