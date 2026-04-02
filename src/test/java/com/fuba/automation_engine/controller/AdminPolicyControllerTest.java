package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ListResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.LookupResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.MutationResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.MutationStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.PolicyView;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ReadStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPolicyController.class)
class AdminPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutomationPolicyService automationPolicyService;

    @Test
    void shouldGetActivePolicy() throws Exception {
        when(automationPolicyService.getActivePolicy("ASSIGNMENT", "FOLLOW_UP_SLA"))
                .thenReturn(new LookupResult(ReadStatus.SUCCESS, samplePolicy(10L, PolicyStatus.ACTIVE, 1L)));

        mockMvc.perform(get("/admin/policies/ASSIGNMENT/FOLLOW_UP_SLA/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dueAfterMinutes").value(15));
    }

    @Test
    void shouldReturnBadRequestForInvalidActiveScope() throws Exception {
        when(automationPolicyService.getActivePolicy(anyString(), eq("FOLLOW_UP_SLA")))
                .thenReturn(new LookupResult(ReadStatus.INVALID_INPUT, null));

        mockMvc.perform(get("/admin/policies/%20/FOLLOW_UP_SLA/active"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForMissingActivePolicy() throws Exception {
        when(automationPolicyService.getActivePolicy("ASSIGNMENT", "MISSING"))
                .thenReturn(new LookupResult(ReadStatus.NOT_FOUND, null));

        mockMvc.perform(get("/admin/policies/ASSIGNMENT/MISSING/active"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListPoliciesForScope() throws Exception {
        when(automationPolicyService.listPolicies("ASSIGNMENT", "FOLLOW_UP_SLA"))
                .thenReturn(new ListResult(
                        ReadStatus.SUCCESS,
                        List.of(
                                samplePolicy(20L, PolicyStatus.INACTIVE, 4L),
                                samplePolicy(10L, PolicyStatus.ACTIVE, 1L))));

        mockMvc.perform(get("/admin/policies").param("domain", "ASSIGNMENT").param("policyKey", "FOLLOW_UP_SLA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$[1].id").value(10))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void shouldReturnBadRequestForInvalidListScope() throws Exception {
        when(automationPolicyService.listPolicies("ASSIGNMENT", " "))
                .thenReturn(new ListResult(ReadStatus.INVALID_INPUT, List.of()));

        mockMvc.perform(get("/admin/policies").param("domain", "ASSIGNMENT").param("policyKey", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreatePolicy() throws Exception {
        when(automationPolicyService.createPolicy(any()))
                .thenReturn(new MutationResult(MutationStatus.SUCCESS, samplePolicy(30L, PolicyStatus.INACTIVE, 0L)));

        String requestJson = """
                {"domain":"assignment","policyKey":"follow_up_sla","enabled":true,"dueAfterMinutes":25}
                """;

        mockMvc.perform(post("/admin/policies").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldReturnBadRequestForInvalidCreate() throws Exception {
        when(automationPolicyService.createPolicy(any())).thenReturn(new MutationResult(MutationStatus.INVALID_INPUT, null));

        String requestJson = """
                {"domain":" ","policyKey":"follow_up_sla","enabled":true,"dueAfterMinutes":10}
                """;

        mockMvc.perform(post("/admin/policies").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdatePolicy() throws Exception {
        when(automationPolicyService.updatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.SUCCESS, samplePolicy(99L, PolicyStatus.INACTIVE, 3L)));

        String requestJson = """
                {"enabled":false,"dueAfterMinutes":20,"expectedVersion":2}
                """;

        mockMvc.perform(put("/admin/policies/99").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void shouldReturnConflictForStaleUpdateVersion() throws Exception {
        when(automationPolicyService.updatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.STALE_VERSION, null));

        String requestJson = """
                {"enabled":false,"dueAfterMinutes":20,"expectedVersion":2}
                """;

        mockMvc.perform(put("/admin/policies/99").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundForUpdate() throws Exception {
        when(automationPolicyService.updatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.NOT_FOUND, null));

        String requestJson = """
                {"enabled":false,"dueAfterMinutes":20,"expectedVersion":2}
                """;

        mockMvc.perform(put("/admin/policies/99").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestForInvalidUpdate() throws Exception {
        when(automationPolicyService.updatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.INVALID_INPUT, null));

        String requestJson = """
                {"enabled":false,"dueAfterMinutes":0,"expectedVersion":2}
                """;

        mockMvc.perform(put("/admin/policies/99").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldActivatePolicy() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.SUCCESS, samplePolicy(99L, PolicyStatus.ACTIVE, 4L)));

        String requestJson = """
                {"expectedVersion":3}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnConflictForStaleActivateVersion() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.STALE_VERSION, null));

        String requestJson = """
                {"expectedVersion":3}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictForActivationConflict() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.ACTIVE_CONFLICT, null));

        String requestJson = """
                {"expectedVersion":3}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnConflictMessageForActivationConflict() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.ACTIVE_CONFLICT, null));

        String requestJson = """
                {"expectedVersion":3}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(content().string("Policy activation conflict"));
    }

    @Test
    void shouldReturnNotFoundForActivate() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.NOT_FOUND, null));

        String requestJson = """
                {"expectedVersion":3}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestForInvalidActivate() throws Exception {
        when(automationPolicyService.activatePolicy(eq(99L), any()))
                .thenReturn(new MutationResult(MutationStatus.INVALID_INPUT, null));

        String requestJson = """
                {"expectedVersion":null}
                """;

        mockMvc.perform(post("/admin/policies/99/activate").contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private PolicyView samplePolicy(long id, PolicyStatus status, long version) {
        return new PolicyView(id, "ASSIGNMENT", "FOLLOW_UP_SLA", true, 15, status, version);
    }

}
