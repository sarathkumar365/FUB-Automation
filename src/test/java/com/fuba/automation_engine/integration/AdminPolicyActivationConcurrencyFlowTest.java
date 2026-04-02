package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import java.util.List;
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
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true, 15));
        AutomationPolicyEntity second = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true, 25));

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
            boolean enabled,
            int dueAfterMinutes) {
        AutomationPolicyEntity policy = new AutomationPolicyEntity();
        policy.setDomain(domain);
        policy.setPolicyKey(policyKey);
        policy.setStatus(status);
        policy.setEnabled(enabled);
        policy.setDueAfterMinutes(dueAfterMinutes);
        return policy;
    }
}
