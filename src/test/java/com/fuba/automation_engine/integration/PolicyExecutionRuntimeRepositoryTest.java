package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class PolicyExecutionRuntimeRepositoryTest {

    @Autowired
    private PolicyExecutionRunRepository runRepository;

    @Autowired
    private PolicyExecutionStepRepository stepRepository;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldEnforceIdempotencyKeyUniqueness() {
        runRepository.saveAndFlush(buildRun("run-key-1"));
        assertThrows(DataIntegrityViolationException.class, () -> runRepository.saveAndFlush(buildRun("run-key-1")));
    }

    @Test
    void shouldEnforceUniqueStepOrderWithinRun() {
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(buildRun("run-key-2"));

        stepRepository.saveAndFlush(buildStep(
                run.getId(),
                1,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                PolicyExecutionStepStatus.PENDING,
                OffsetDateTime.now().plusMinutes(5),
                null));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> stepRepository.saveAndFlush(buildStep(
                        run.getId(),
                        1,
                        PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                        PolicyExecutionStepStatus.WAITING_DEPENDENCY,
                        null,
                        1)));
    }

    @Test
    void shouldReturnDuePendingStepsOnlyOrderedByDueAtThenId() {
        OffsetDateTime now = OffsetDateTime.now();
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(buildRun("run-key-3"));

        PolicyExecutionStepEntity dueFirst = stepRepository.saveAndFlush(buildStep(
                run.getId(),
                1,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                PolicyExecutionStepStatus.PENDING,
                now.minusMinutes(2),
                null));

        stepRepository.saveAndFlush(buildStep(
                run.getId(),
                2,
                PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                PolicyExecutionStepStatus.WAITING_DEPENDENCY,
                now.minusMinutes(1),
                1));

        stepRepository.saveAndFlush(buildStep(
                run.getId(),
                3,
                PolicyStepType.ON_FAILURE_EXECUTE_ACTION,
                PolicyExecutionStepStatus.PENDING,
                now.plusMinutes(1),
                2));

        PolicyExecutionStepEntity dueSecond = stepRepository.saveAndFlush(buildStep(
                run.getId(),
                4,
                PolicyStepType.ON_FAILURE_EXECUTE_ACTION,
                PolicyExecutionStepStatus.PENDING,
                now.minusMinutes(1),
                2));

        List<PolicyExecutionStepEntity> due = stepRepository.findByStatusAndDueAtLessThanEqualOrderByDueAtAscIdAsc(
                PolicyExecutionStepStatus.PENDING,
                now);

        assertEquals(2, due.size());
        assertEquals(dueFirst.getId(), due.get(0).getId());
        assertEquals(dueSecond.getId(), due.get(1).getId());
    }

    @Test
    void shouldReturnRunStepsInOrder() {
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(buildRun("run-key-4"));

        stepRepository.saveAndFlush(buildStep(
                run.getId(),
                2,
                PolicyStepType.WAIT_AND_CHECK_COMMUNICATION,
                PolicyExecutionStepStatus.WAITING_DEPENDENCY,
                null,
                1));
        stepRepository.saveAndFlush(buildStep(
                run.getId(),
                1,
                PolicyStepType.WAIT_AND_CHECK_CLAIM,
                PolicyExecutionStepStatus.PENDING,
                OffsetDateTime.now().plusMinutes(5),
                null));

        List<PolicyExecutionStepEntity> steps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        assertEquals(2, steps.size());
        assertEquals(1, steps.get(0).getStepOrder());
        assertEquals(2, steps.get(1).getStepOrder());
    }

    private PolicyExecutionRunEntity buildRun(String idempotencyKey) {
        PolicyExecutionRunEntity entity = new PolicyExecutionRunEntity();
        entity.setSource(WebhookSource.INTERNAL);
        entity.setEventId("evt-1");
        entity.setWebhookEventId(null);
        entity.setSourceLeadId("source-lead-1");
        entity.setDomain("ASSIGNMENT");
        entity.setPolicyKey("FOLLOW_UP_SLA");
        entity.setPolicyVersion(1L);
        entity.setPolicyBlueprintSnapshot(validBlueprintSnapshot());
        entity.setStatus(PolicyExecutionRunStatus.PENDING);
        entity.setReasonCode(null);
        entity.setIdempotencyKey(idempotencyKey);
        return entity;
    }

    private PolicyExecutionStepEntity buildStep(
            Long runId,
            int stepOrder,
            PolicyStepType stepType,
            PolicyExecutionStepStatus status,
            OffsetDateTime dueAt,
            Integer dependsOnStepOrder) {
        PolicyExecutionStepEntity entity = new PolicyExecutionStepEntity();
        entity.setRunId(runId);
        entity.setStepOrder(stepOrder);
        entity.setStepType(stepType);
        entity.setStatus(status);
        entity.setDueAt(dueAt);
        entity.setDependsOnStepOrder(dependsOnStepOrder);
        return entity;
    }

    private Map<String, Object> validBlueprintSnapshot() {
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
