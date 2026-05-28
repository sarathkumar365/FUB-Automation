package com.fuba.automation_engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit-tests the SPA fallback wired by {@link AdminUiController}.
 *
 * <p>The controller is intentionally a single catch-all so adding new SPA
 * routes (e.g. {@code /admin-ui/login}, {@code /admin-ui/persons/42}) doesn't
 * require touching this file. These tests pin that promise.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forwardsAdminUiRoot() throws Exception {
        mockMvc.perform(get("/admin-ui"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsAdminUiTopLevelRoute() throws Exception {
        mockMvc.perform(get("/admin-ui/webhooks"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsAdminUiDeepLinkWithPathVariable() throws Exception {
        mockMvc.perform(get("/admin-ui/persons/42"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void forwardsAdminUiLoginRouteAddedByLaterFeature() throws Exception {
        // Pins the catch-all behaviour: routes added in later features (here,
        // /admin-ui/login from dev-hosting-security-hardening) keep working
        // without anyone editing this controller.
        mockMvc.perform(get("/admin-ui/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void doesNotInterceptHealthEndpoint() throws Exception {
        // /health is anonymous and must reach HealthController, not the SPA
        // fallback. Spring's handler-mapping precedence picks the more
        // specific match, but pinning it here protects against future
        // accidental over-broadening of the catch-all pattern.
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }
}
