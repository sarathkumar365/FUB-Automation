package com.fuba.automation_engine.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyAdminApiCutoverIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNotFoundForLegacyPolicyAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/policies"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/policies/ASSIGNMENT/FOLLOW_UP_SLA/active"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/policies/1/activate")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/policy-executions"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/policy-executions/1"))
                .andExpect(status().isNotFound());
    }
}
