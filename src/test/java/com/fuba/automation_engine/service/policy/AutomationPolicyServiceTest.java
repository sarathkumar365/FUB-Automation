package com.fuba.automation_engine.service.policy;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ActivatePolicyCommand;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.CreatePolicyCommand;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ListResult;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.MutationStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.PolicyView;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.ReadStatus;
import com.fuba.automation_engine.service.policy.AutomationPolicyService.UpdatePolicyCommand;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AutomationPolicyServiceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private AutomationPolicyRepository repository;

    private AutomationPolicyService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        service = new AutomationPolicyService(repository);
    }

    @Test
    void shouldGetActivePolicyForScope() {
        repository.saveAndFlush(policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true));

        var result = service.getActivePolicy(" assignment ", " follow_up_sla ");

        assertEquals(ReadStatus.SUCCESS, result.status());
        assertNotNull(result.policy());
        assertEquals("ASSIGNMENT", result.policy().domain());
        assertEquals("FOLLOW_UP_SLA", result.policy().policyKey());
    }

    @Test
    void shouldReturnInvalidInputForInvalidGetActiveScope() {
        var result = service.getActivePolicy(" ", "FOLLOW_UP_SLA");

        assertEquals(ReadStatus.INVALID_INPUT, result.status());
        assertNull(result.policy());
    }

    @Test
    void shouldCreatePolicyAsInactiveByDefault() {
        var result = service.createPolicy(new CreatePolicyCommand("assignment", "follow_up_sla", true, validBlueprint()));

        assertEquals(MutationStatus.SUCCESS, result.status());
        assertNotNull(result.policy());
        assertEquals(PolicyStatus.INACTIVE, result.policy().status());
        assertEquals("ASSIGNMENT", result.policy().domain());
        assertEquals("FOLLOW_UP_SLA", result.policy().policyKey());
    }

    @Test
    void shouldUpdatePolicySuccessfully() {
        AutomationPolicyEntity created = repository.saveAndFlush(policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));

        var result = service.updatePolicy(created.getId(), new UpdatePolicyCommand(false, created.getVersion(), validBlueprint()));

        assertEquals(MutationStatus.SUCCESS, result.status());
        assertEquals(false, result.policy().enabled());
    }

    @Test
    void shouldReturnStaleVersionWhenExpectedVersionDoesNotMatch() {
        AutomationPolicyEntity created = repository.saveAndFlush(policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));

        var result = service.updatePolicy(
                created.getId(),
                new UpdatePolicyCommand(false, created.getVersion() + 1, validBlueprint()));

        assertEquals(MutationStatus.STALE_VERSION, result.status());
    }

    @Test
    void shouldMapOptimisticLockConflictOnUpdateToStaleVersion() {
        AutomationPolicyRepository mocked = Mockito.mock(AutomationPolicyRepository.class);
        AutomationPolicyService conflictedService = new AutomationPolicyService(mocked);

        AutomationPolicyEntity target = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true);
        target.setId(110L);
        target.setVersion(4L);
        Mockito.when(mocked.findById(110L)).thenReturn(Optional.of(target));
        Mockito.when(mocked.saveAndFlush(Mockito.any(AutomationPolicyEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(AutomationPolicyEntity.class, 110L));

        var result = conflictedService.updatePolicy(110L, new UpdatePolicyCommand(false, 4L, validBlueprint()));

        assertEquals(MutationStatus.STALE_VERSION, result.status());
    }

    @Test
    void shouldActivatePolicyAndDeactivatePreviousActiveInScope() {
        AutomationPolicyEntity active = repository.saveAndFlush(policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true));
        AutomationPolicyEntity target = repository.saveAndFlush(policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true));

        var result = service.activatePolicy(target.getId(), new ActivatePolicyCommand(target.getVersion()));

        assertEquals(MutationStatus.SUCCESS, result.status());
        assertEquals(PolicyStatus.ACTIVE, result.policy().status());
        AutomationPolicyEntity refreshedPrevious = repository.findById(active.getId()).orElseThrow();
        assertEquals(PolicyStatus.INACTIVE, refreshedPrevious.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenActivateTargetMissing() {
        var result = service.activatePolicy(999999L, new ActivatePolicyCommand(0L));

        assertEquals(MutationStatus.NOT_FOUND, result.status());
    }

    @Test
    void shouldReturnInvalidInputForBadCommands() {
        var createResult = service.createPolicy(new CreatePolicyCommand(" ", "follow_up_sla", true, validBlueprint()));
        var updateResult = service.updatePolicy(1L, new UpdatePolicyCommand(true, null, validBlueprint()));
        var activateResult = service.activatePolicy(1L, new ActivatePolicyCommand(null));

        assertEquals(MutationStatus.INVALID_INPUT, createResult.status());
        assertEquals(MutationStatus.INVALID_INPUT, updateResult.status());
        assertEquals(MutationStatus.INVALID_INPUT, activateResult.status());
    }

    @Test
    void shouldListPoliciesNewestFirst() {
        service.createPolicy(new CreatePolicyCommand("ASSIGNMENT", "FOLLOW_UP_SLA", true, validBlueprint()));
        service.createPolicy(new CreatePolicyCommand("ASSIGNMENT", "FOLLOW_UP_SLA", false, validBlueprint()));

        ListResult result = service.listPolicies("ASSIGNMENT", "FOLLOW_UP_SLA");
        List<PolicyView> list = result.policies();

        assertEquals(ReadStatus.SUCCESS, result.status());
        assertFalse(list.isEmpty());
        assertTrue(list.get(0).id() >= list.get(1).id());
    }

    @Test
    void shouldReturnInvalidInputForInvalidListScope() {
        ListResult result = service.listPolicies("ASSIGNMENT", " ");

        assertEquals(ReadStatus.INVALID_INPUT, result.status());
        assertTrue(result.policies().isEmpty());
    }

    @Test
    void shouldMapOptimisticLockConflictToStaleVersion() {
        AutomationPolicyRepository mocked = Mockito.mock(AutomationPolicyRepository.class);
        AutomationPolicyService conflictedService = new AutomationPolicyService(mocked);

        AutomationPolicyEntity target = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true);
        target.setId(100L);
        target.setVersion(2L);
        Mockito.when(mocked.findById(100L)).thenReturn(Optional.of(target));
        Mockito.when(mocked.saveAndFlush(Mockito.any(AutomationPolicyEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(AutomationPolicyEntity.class, 100L));

        var result = conflictedService.activatePolicy(100L, new ActivatePolicyCommand(2L));

        assertEquals(MutationStatus.STALE_VERSION, result.status());
    }

    @Test
    void shouldMapScopedActiveConflictToActiveConflict() {
        AutomationPolicyRepository mocked = Mockito.mock(AutomationPolicyRepository.class);
        AutomationPolicyService conflictedService = new AutomationPolicyService(mocked);

        AutomationPolicyEntity target = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true);
        target.setId(200L);
        target.setVersion(3L);
        Mockito.when(mocked.findById(200L)).thenReturn(Optional.of(target));
        Mockito.when(mocked.findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc(
                        "ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        Mockito.when(mocked.saveAndFlush(Mockito.any(AutomationPolicyEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_automation_policies_active_per_scope\""));

        var result = conflictedService.activatePolicy(200L, new ActivatePolicyCommand(3L));

        assertEquals(MutationStatus.ACTIVE_CONFLICT, result.status());
    }

    @Test
    void shouldMapNonScopeDataIntegrityViolationToInvalidInput() {
        AutomationPolicyRepository mocked = Mockito.mock(AutomationPolicyRepository.class);
        AutomationPolicyService conflictedService = new AutomationPolicyService(mocked);
        Mockito.when(mocked.saveAndFlush(Mockito.any(AutomationPolicyEntity.class)))
                .thenThrow(new DataIntegrityViolationException("value too long for type character varying(64)"));

        var result = conflictedService.createPolicy(
                new CreatePolicyCommand("ASSIGNMENT", "FOLLOW_UP_SLA", true, validBlueprint()));

        assertEquals(MutationStatus.INVALID_INPUT, result.status());
    }

    @Test
    void shouldMapNonScopeDataIntegrityViolationOnActivateToInvalidInput() {
        AutomationPolicyRepository mocked = Mockito.mock(AutomationPolicyRepository.class);
        AutomationPolicyService conflictedService = new AutomationPolicyService(mocked);

        AutomationPolicyEntity target = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true);
        target.setId(220L);
        target.setVersion(1L);
        Mockito.when(mocked.findById(220L)).thenReturn(Optional.of(target));
        Mockito.when(mocked.saveAndFlush(Mockito.any(AutomationPolicyEntity.class)))
                .thenThrow(new DataIntegrityViolationException("check constraint violation"));

        var result = conflictedService.activatePolicy(220L, new ActivatePolicyCommand(1L));

        assertEquals(MutationStatus.INVALID_INPUT, result.status());
    }

    @Test
    void shouldRejectCreateWhenNormalizedScopeExceedsColumnLength() {
        String longDomain = "A".repeat(65);

        var result = service.createPolicy(new CreatePolicyCommand(longDomain, "FOLLOW_UP_SLA", true, validBlueprint()));

        assertEquals(MutationStatus.INVALID_INPUT, result.status());
    }

    @Test
    void shouldRejectCreateWhenBlueprintInvalid() {
        Map<String, Object> invalidBlueprint = Map.of();
        var result = service.createPolicy(
                new CreatePolicyCommand("ASSIGNMENT", "FOLLOW_UP_SLA", true, invalidBlueprint));
        assertEquals(MutationStatus.INVALID_POLICY_BLUEPRINT, result.status());
    }

    @Test
    void shouldRejectActivateWhenBlueprintInvalid() {
        AutomationPolicyEntity policy = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.INACTIVE, true);
        policy.setBlueprint(Map.of());
        AutomationPolicyEntity saved = repository.saveAndFlush(policy);

        var result = service.activatePolicy(saved.getId(), new ActivatePolicyCommand(saved.getVersion()));

        assertEquals(MutationStatus.INVALID_POLICY_BLUEPRINT, result.status());
    }

    @Test
    void shouldReturnPolicyInvalidForInvalidActiveBlueprint() {
        AutomationPolicyEntity policy = policy("ASSIGNMENT", "FOLLOW_UP_SLA", PolicyStatus.ACTIVE, true);
        policy.setBlueprint(Map.of());
        repository.saveAndFlush(policy);

        var result = service.getActivePolicy("ASSIGNMENT", "FOLLOW_UP_SLA");

        assertEquals(ReadStatus.POLICY_INVALID, result.status());
        assertNull(result.policy());
    }

    private AutomationPolicyEntity policy(
            String domain,
            String policyKey,
            PolicyStatus status,
            boolean enabled) {
        AutomationPolicyEntity policy = new AutomationPolicyEntity();
        policy.setDomain(domain);
        policy.setPolicyKey(policyKey);
        policy.setStatus(status);
        policy.setEnabled(enabled);
        policy.setBlueprint(validBlueprint());
        return policy;
    }

    private Map<String, Object> validBlueprint() {
        return Map.of(
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
                Map.of("actionType", "REASSIGN"));
    }
}
