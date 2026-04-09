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
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true));
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, false));

        AutomationPolicyEntity active = repository
                .findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE)
                .orElseThrow();

        assertEquals(PolicyStatus.ACTIVE, active.getStatus());
        assertTrue(active.isEnabled());
    }

    @Test
    void shouldReturnPoliciesNewestFirstWithinScope() {
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, false));

        List<AutomationPolicyEntity> policies =
                repository.findByDomainAndPolicyKeyOrderByIdDesc("ASSIGNMENT", "FOLLOW_UP_SLA");

        assertFalse(policies.isEmpty());
        assertTrue(policies.get(0).getId() >= policies.get(1).getId());
    }

    @Test
    void shouldIncrementVersionOnSuccessfulUpdate() {
        AutomationPolicyEntity saved = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));
        Long before = saved.getVersion();

        saved.setEnabled(false);
        AutomationPolicyEntity updated = repository.saveAndFlush(saved);

        assertEquals(before + 1, updated.getVersion());
    }

    @Test
    void shouldRejectStaleVersionUpdate() {
        AutomationPolicyEntity created = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));

        AutomationPolicyEntity stale = new AutomationPolicyEntity();
        stale.setId(created.getId());
        stale.setDomain(created.getDomain());
        stale.setPolicyKey(created.getPolicyKey());
        stale.setEnabled(created.isEnabled());
        stale.setBlueprint(created.getBlueprint());
        stale.setStatus(created.getStatus());
        stale.setVersion(created.getVersion());

        created.setEnabled(false);
        repository.saveAndFlush(created);

        stale.setEnabled(true);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
    }

    @Test
    void shouldDeactivateActivePoliciesInScopeExcludingTarget() {
        AutomationPolicyEntity active = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true));
        AutomationPolicyEntity target = repository.saveAndFlush(
                buildPolicy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));
        repository.saveAndFlush(buildPolicy("ASSIGNMENT", "OTHER_POLICY", PolicyStatus.ACTIVE, true));

        int changed = repository.deactivateActivePoliciesInScopeExcludingId(
                "ASSIGNMENT",
                "FOLLOW_UP_SLA",
                target.getId(),
                PolicyStatus.ACTIVE,
                PolicyStatus.INACTIVE);

        assertEquals(1, changed);
        AutomationPolicyEntity refreshedActive = repository.findById(active.getId()).orElseThrow();
        assertEquals(PolicyStatus.INACTIVE, refreshedActive.getStatus());
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
        policy.setBlueprint(java.util.Map.of(
                "templateKey",
                "assignment_followup_sla_v1",
                "steps",
                java.util.List.of(
                        java.util.Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", 5),
                        java.util.Map.of(
                                "type",
                                "WAIT_AND_CHECK_COMMUNICATION",
                                "delayMinutes",
                                10,
                                "dependsOn",
                                "WAIT_AND_CHECK_CLAIM"),
                        java.util.Map.of("type", "ON_FAILURE_EXECUTE_ACTION", "dependsOn", "WAIT_AND_CHECK_COMMUNICATION")),
                "actionConfig",
                java.util.Map.of("actionType", "REASSIGN", "targetUserId", 77L)));
        return policy;
    }
}
