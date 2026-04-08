package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import com.fuba.automation_engine.persistence.repository.AutomationPolicyRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.AutomationPolicyService;
import com.fuba.automation_engine.service.policy.PolicyExecutionManager;
import com.fuba.automation_engine.service.policy.PolicyExecutionPlanRequest;
import com.fuba.automation_engine.service.policy.PolicyExecutionPlanningResult;
import com.fuba.automation_engine.service.policy.PolicyExecutionMaterializationContract;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({
        AutomationPolicyService.class,
        PolicyExecutionManager.class
})
class PolicyExecutionManagerIntegrationTest {

    @Autowired
    private AutomationPolicyRepository policyRepository;

    @Autowired
    private PolicyExecutionRunRepository runRepository;

    @Autowired
    private PolicyExecutionStepRepository stepRepository;

    @Autowired
    private PolicyExecutionManager manager;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    void shouldCreatePendingRunAndMaterializeInitialSteps() {
        seedActivePolicy(validBlueprint(5, 10));
        OffsetDateTime before = OffsetDateTime.now();

        PolicyExecutionPlanningResult result = manager.plan(defaultRequest("evt-1", "hash-1"));

        assertEquals(PolicyExecutionRunStatus.PENDING, result.status());
        assertNotNull(result.runId());
        assertNull(result.reasonCode());

        PolicyExecutionRunEntity run = runRepository.findById(result.runId()).orElseThrow();
        assertEquals("ASSIGNMENT", run.getDomain());
        assertEquals("FOLLOW_UP_SLA", run.getPolicyKey());
        assertEquals("source-lead-1", run.getSourceLeadId());
        assertEquals(PolicyExecutionRunStatus.PENDING, run.getStatus());

        List<PolicyExecutionStepEntity> steps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        assertEquals(3, steps.size());

        PolicyExecutionStepEntity claim = steps.get(0);
        assertEquals(PolicyExecutionMaterializationContract.CLAIM_STEP_ORDER, claim.getStepOrder());
        assertEquals(PolicyStepType.WAIT_AND_CHECK_CLAIM, claim.getStepType());
        assertEquals(PolicyExecutionStepStatus.PENDING, claim.getStatus());
        assertNotNull(claim.getDueAt());
        assertTrue(!claim.getDueAt().isBefore(before.plusMinutes(4)));
        assertTrue(claim.getDueAt().isBefore(before.plusMinutes(7)));

        PolicyExecutionStepEntity communication = steps.get(1);
        assertEquals(PolicyExecutionMaterializationContract.COMMUNICATION_STEP_ORDER, communication.getStepOrder());
        assertEquals(PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, communication.getStepType());
        assertEquals(PolicyExecutionStepStatus.WAITING_DEPENDENCY, communication.getStatus());
        assertNull(communication.getDueAt());
        assertEquals(PolicyExecutionMaterializationContract.CLAIM_STEP_ORDER, communication.getDependsOnStepOrder());

        PolicyExecutionStepEntity action = steps.get(2);
        assertEquals(PolicyExecutionMaterializationContract.ACTION_STEP_ORDER, action.getStepOrder());
        assertEquals(PolicyStepType.ON_FAILURE_EXECUTE_ACTION, action.getStepType());
        assertEquals(PolicyExecutionStepStatus.WAITING_DEPENDENCY, action.getStatus());
        assertNull(action.getDueAt());
        assertEquals(PolicyExecutionMaterializationContract.COMMUNICATION_STEP_ORDER, action.getDependsOnStepOrder());
    }

    @Test
    void shouldPersistBlockedPolicyRunWhenActivePolicyMissing() {
        PolicyExecutionPlanningResult result = manager.plan(defaultRequest("evt-2", "hash-2"));

        assertEquals(PolicyExecutionRunStatus.BLOCKED_POLICY, result.status());
        assertNotNull(result.runId());
        assertEquals(0, stepRepository.findByRunIdOrderByStepOrderAsc(result.runId()).size());
    }

    @Test
    void shouldCreatePendingRunWhenIdentityResolverIsRemoved() {
        seedActivePolicy(validBlueprint(5, 10));

        PolicyExecutionPlanningResult result = manager.plan(defaultRequest("evt-3", "hash-3"));

        assertEquals(PolicyExecutionRunStatus.PENDING, result.status());
        assertNotNull(result.runId());
        assertEquals(3, stepRepository.findByRunIdOrderByStepOrderAsc(result.runId()).size());
    }

    @Test
    void shouldSuppressDuplicateRunsByIdempotencyKey() {
        seedActivePolicy(validBlueprint(5, 10));
        PolicyExecutionPlanRequest request = defaultRequest("evt-4", "hash-4");

        PolicyExecutionPlanningResult first = manager.plan(request);
        PolicyExecutionPlanningResult second = manager.plan(request);

        assertEquals(PolicyExecutionRunStatus.PENDING, first.status());
        assertEquals(PolicyExecutionRunStatus.DUPLICATE_IGNORED, second.status());
        assertEquals(first.runId(), second.runId());
        assertEquals(1, runRepository.count());
        assertEquals(3, stepRepository.findByRunIdOrderByStepOrderAsc(first.runId()).size());
    }

    @Test
    void shouldKeepRunSnapshotImmutableAfterPolicyChanges() {
        AutomationPolicyEntity policy = seedActivePolicy(validBlueprint(5, 10));
        PolicyExecutionPlanningResult result = manager.plan(defaultRequest("evt-5", "hash-5"));
        Long runId = result.runId();

        policy.setBlueprint(validBlueprint(30, 45));
        policyRepository.saveAndFlush(policy);

        PolicyExecutionRunEntity run = runRepository.findById(runId).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) run.getPolicyBlueprintSnapshot().get("steps");
        Number claimDelay = (Number) steps.get(0).get("delayMinutes");
        Number communicationDelay = (Number) steps.get(1).get("delayMinutes");
        assertEquals(5, claimDelay.intValue());
        assertEquals(10, communicationDelay.intValue());
    }

    @Test
    void shouldSupportLargePayloadHashWithoutIdempotencyKeyOverflow() {
        seedActivePolicy(validBlueprint(5, 10));
        String largePayloadHash = "h".repeat(1024);
        PolicyExecutionPlanRequest request = new PolicyExecutionPlanRequest(
                WebhookSource.FUB,
                null,
                largePayloadHash,
                "source-lead-1",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                "ASSIGNMENT",
                "FOLLOW_UP_SLA",
                null,
                Map.of("sourceEventType", "peopleCreated"));

        PolicyExecutionPlanningResult result = manager.plan(request);
        PolicyExecutionRunEntity run = runRepository.findById(result.runId()).orElseThrow();

        assertEquals(PolicyExecutionRunStatus.PENDING, result.status());
        assertTrue(run.getIdempotencyKey().startsWith("PEM1|"));
        assertTrue(run.getIdempotencyKey().length() <= 255);
    }

    @Test
    void shouldNotTreatNonDuplicateIntegrityErrorAsDuplicate() {
        seedActivePolicy(validBlueprint(5, 10));
        String oversizedSourceLeadId = "l".repeat(300);
        PolicyExecutionPlanRequest request = new PolicyExecutionPlanRequest(
                WebhookSource.FUB,
                "evt-too-long-source-lead",
                "hash-too-long-source-lead",
                oversizedSourceLeadId,
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                "ASSIGNMENT",
                "FOLLOW_UP_SLA",
                null,
                Map.of("sourceEventType", "peopleCreated"));

        assertThrows(DataIntegrityViolationException.class, () -> manager.plan(request));
    }

    private AutomationPolicyEntity seedActivePolicy(Map<String, Object> blueprint) {
        AutomationPolicyEntity policy = new AutomationPolicyEntity();
        policy.setDomain("ASSIGNMENT");
        policy.setPolicyKey("FOLLOW_UP_SLA");
        policy.setEnabled(true);
        policy.setBlueprint(blueprint);
        policy.setStatus(PolicyStatus.ACTIVE);
        return policyRepository.saveAndFlush(policy);
    }

    private PolicyExecutionPlanRequest defaultRequest(String eventId, String payloadHash) {
        return new PolicyExecutionPlanRequest(
                WebhookSource.FUB,
                eventId,
                payloadHash,
                "source-lead-1",
                NormalizedDomain.ASSIGNMENT,
                NormalizedAction.CREATED,
                "ASSIGNMENT",
                "FOLLOW_UP_SLA",
                null,
                Map.of("sourceEventType", "peopleCreated"));
    }

    private Map<String, Object> validBlueprint(int claimDelayMinutes, int communicationDelayMinutes) {
        return Map.of(
                "templateKey",
                "assignment_followup_sla_v1",
                "steps",
                List.of(
                        Map.of("type", "WAIT_AND_CHECK_CLAIM", "delayMinutes", claimDelayMinutes),
                        Map.of(
                                "type",
                                "WAIT_AND_CHECK_COMMUNICATION",
                                "delayMinutes",
                                communicationDelayMinutes,
                                "dependsOn",
                                "WAIT_AND_CHECK_CLAIM"),
                        Map.of("type", "ON_FAILURE_EXECUTE_ACTION", "dependsOn", "WAIT_AND_CHECK_COMMUNICATION")),
                "actionConfig",
                Map.of("actionType", "REASSIGN"));
    }
}
