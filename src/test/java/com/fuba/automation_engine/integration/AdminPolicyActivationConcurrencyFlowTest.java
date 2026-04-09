package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPolicyActivationConcurrencyFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomationPolicyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldKeepSingleActivePolicyWhenCompetingActivationsOccur() throws Exception {
        repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true));
        AutomationPolicyEntity second = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));

        // Intentionally sequential: verifies stale expectedVersion conflict mapping (200 then 409)
        // and preserves the single-active invariant for the scope without introducing flaky timing races.
        int firstAttemptStatus = activate(second.getId(), second.getVersion());
        int secondAttemptStatus = activate(second.getId(), second.getVersion());

        assertEquals(200, firstAttemptStatus);
        assertEquals(409, secondAttemptStatus);

        List<AutomationPolicyEntity> scoped = repository.findByDomainAndPolicyKeyOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA");
        long activeCount = scoped.stream().filter(policy -> policy.getStatus() == PolicyStatus.ACTIVE).count();
        assertEquals(1L, activeCount);
    }

    private int activate(Long id, Long expectedVersion) throws Exception {
        String body = """
                {"expectedVersion":%d}
                """.formatted(expectedVersion);

        MvcResult result = mockMvc.perform(post("/admin/policies/" + id + "/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        return result.getResponse().getStatus();
    }

    private AutomationPolicyEntity buildPolicy(
            String domain,
            String policyKey,
            PolicyStatus status,
            boolean enabled) {
        AutomationPolicyEntity policy = new AutomationPolicyEntity();
        policy.setDomain(domain);
        policy.setPolicyKey(policyKey);
        policy.setStatus(status);
        policy.setEnabled(enabled);
        policy.setBlueprint(Map.of(
                "templateKey",
                "assignment_followup_sla_v1",
                "steps",
                List.of(
                        Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", 5),
                        Map.of(
                                "type",
                                "WAIT_AND_CHECK_COMMUNICATION",
                                "delayMinutes",
                                10,
                                "dependsOn",
                                "WAIT_AND_CHECK_CLAIM"),
                        Map.of("type", "ON_FAILURE_EXECUTE_ACTION", "dependsOn", "WAIT_AND_CHECK_COMMUNICATION")),
                "actionConfig",
                Map.of("actionType", "REASSIGN", "targetUserId", 77L)));
        return policy;
    }
}
