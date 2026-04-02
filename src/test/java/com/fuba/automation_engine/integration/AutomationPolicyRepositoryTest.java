package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AutomationPolicyRepositoryTest {

    @Autowired
    private AutomationPolicyRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldFindActivePolicyByScope() {
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true, 15));
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, false, 20));

        AutomationPolicyEntity active = repository
                .findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE)
                .orElseThrow();

        assertEquals(PolicyStatus.ACTIVE, active.getStatus());
        assertTrue(active.isEnabled());
        assertEquals(15, active.getDueAfterMinutes());
    }

    @Test
    void shouldReturnPoliciesNewestFirstWithinScope() {
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true, 20));
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, false, 30));

        List<AutomationPolicyEntity> policies =
                repository.findByDomainAndPolicyKeyOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA");

        assertFalse(policies.isEmpty());
        assertTrue(policies.get(0).getId() >= policies.get(1).getId());
    }

    @Test
    void shouldIncrementVersionOnSuccessfulUpdate() {
        AutomationPolicyEntity saved = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true, 18));
        Long before = saved.getVersion();

        saved.setDueAfterMinutes(19);
        AutomationPolicyEntity updated = repository.saveAndFlush(saved);

        assertEquals(before + 1, updated.getVersion());
    }

    @Test
    void shouldRejectStaleVersionUpdate() {
        AutomationPolicyEntity created = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true, 22));

        AutomationPolicyEntity stale = new AutomationPolicyEntity();
        stale.setId(created.getId());
        stale.setDomain(created.getDomain());
        stale.setPolicyKey(created.getPolicyKey());
        stale.setEnabled(created.isEnabled());
        stale.setDueAfterMinutes(created.getDueAfterMinutes());
        stale.setStatus(created.getStatus());
        stale.setVersion(created.getVersion());

        created.setDueAfterMinutes(23);
        repository.saveAndFlush(created);

        stale.setDueAfterMinutes(24);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
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
