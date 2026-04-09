package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository.ClaimedStepRow;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository.StaleRecoveryOutcome;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepClaimRepository.StaleRecoveryRow;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PolicyExecutionStepClaimRepositoryPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("automation_engine")
            .withUsername("automation")
            .withPassword("automation");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private PolicyExecutionStepClaimRepository claimRepository;

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
    void shouldClaimOnlyDuePendingRowsInOrderAndRespectLimit() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-07T15:30:00Z");
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(newRun("IDEMP-A"));

        stepRepository.saveAndFlush(step(run.getId(), 1, PolicyExecutionStepStatus.PENDING, now.minusMinutes(5)));
        stepRepository.saveAndFlush(step(run.getId(), 2, PolicyExecutionStepStatus.PENDING, now.minusMinutes(3)));
        stepRepository.saveAndFlush(step(run.getId(), 3, PolicyExecutionStepStatus.PENDING, now.plusMinutes(10)));
        stepRepository.saveAndFlush(step(run.getId(), 4, PolicyExecutionStepStatus.WAITING_DEPENDENCY, now.minusMinutes(1)));
        stepRepository.saveAndFlush(step(run.getId(), 5, PolicyExecutionStepStatus.COMPLETED, now.minusMinutes(2)));

        List<ClaimedStepRow> claimed = claimRepository.claimDuePendingSteps(now, 2);

        assertEquals(2, claimed.size());
        assertEquals(1, claimed.get(0).stepOrder());
        assertEquals(2, claimed.get(1).stepOrder());
        assertEquals(PolicyExecutionStepStatus.PROCESSING, claimed.get(0).status());
        assertEquals(PolicyExecutionStepStatus.PROCESSING, claimed.get(1).status());

        List<PolicyExecutionStepEntity> allSteps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        assertEquals(PolicyExecutionStepStatus.PROCESSING, allSteps.get(0).getStatus());
        assertEquals(PolicyExecutionStepStatus.PROCESSING, allSteps.get(1).getStatus());
        assertEquals(PolicyExecutionStepStatus.PENDING, allSteps.get(2).getStatus());
        assertEquals(PolicyExecutionStepStatus.WAITING_DEPENDENCY, allSteps.get(3).getStatus());
        assertEquals(PolicyExecutionStepStatus.COMPLETED, allSteps.get(4).getStatus());
    }

    @Test
    void shouldNotReturnOverlappingRowsAcrossConcurrentClaimCalls() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-07T16:00:00Z");
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(newRun("IDEMP-B"));

        stepRepository.saveAndFlush(step(run.getId(), 1, PolicyExecutionStepStatus.PENDING, now.minusMinutes(5)));
        stepRepository.saveAndFlush(step(run.getId(), 2, PolicyExecutionStepStatus.PENDING, now.minusMinutes(4)));
        stepRepository.saveAndFlush(step(run.getId(), 3, PolicyExecutionStepStatus.PENDING, now.minusMinutes(3)));
        stepRepository.saveAndFlush(step(run.getId(), 4, PolicyExecutionStepStatus.PENDING, now.minusMinutes(2)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<ClaimedStepRow>> task = () -> claimRepository.claimDuePendingSteps(now, 2);
            Future<List<ClaimedStepRow>> first = executor.submit(task);
            Future<List<ClaimedStepRow>> second = executor.submit(task);

            List<ClaimedStepRow> firstResult = first.get();
            List<ClaimedStepRow> secondResult = second.get();

            Set<Long> claimedIds = new HashSet<>();
            firstResult.forEach(row -> claimedIds.add(row.id()));
            secondResult.forEach(row -> claimedIds.add(row.id()));

            assertEquals(4, claimedIds.size());
            assertTrue(firstResult.stream().allMatch(row -> row.status() == PolicyExecutionStepStatus.PROCESSING));
            assertTrue(secondResult.stream().allMatch(row -> row.status() == PolicyExecutionStepStatus.PROCESSING));
        } catch (ExecutionException ex) {
            throw new RuntimeException("Concurrent claim execution failed", ex);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRecoverOnlyStaleProcessingRowsWithRequeueThenFailBehavior() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-08T12:00:00Z");
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(newRun("IDEMP-C"));

        PolicyExecutionStepEntity staleRequeue = step(run.getId(), 1, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(20));
        staleRequeue.setStaleRecoveryCount(0);
        PolicyExecutionStepEntity staleFail = step(run.getId(), 2, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(16));
        staleFail.setStaleRecoveryCount(1);
        PolicyExecutionStepEntity freshProcessing = step(run.getId(), 3, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(5));
        freshProcessing.setStaleRecoveryCount(0);

        stepRepository.saveAndFlush(staleRequeue);
        stepRepository.saveAndFlush(staleFail);
        stepRepository.saveAndFlush(freshProcessing);

        List<StaleRecoveryRow> recovered = claimRepository.recoverStaleProcessingSteps(
                now.minusMinutes(15),
                10,
                1,
                now);

        assertEquals(2, recovered.size());
        assertTrue(recovered.stream().anyMatch(row -> row.id() == staleRequeue.getId()
                && row.status() == PolicyExecutionStepStatus.PENDING
                && row.outcome() == StaleRecoveryOutcome.REQUEUED
                && row.staleRecoveryCount() == 1));
        assertTrue(recovered.stream().anyMatch(row -> row.id() == staleFail.getId()
                && row.status() == PolicyExecutionStepStatus.FAILED
                && row.outcome() == StaleRecoveryOutcome.FAILED
                && row.staleRecoveryCount() == 1));

        List<PolicyExecutionStepEntity> allSteps = stepRepository.findByRunIdOrderByStepOrderAsc(run.getId());
        PolicyExecutionStepEntity requeuedStep = allSteps.get(0);
        PolicyExecutionStepEntity failedStep = allSteps.get(1);
        PolicyExecutionStepEntity freshStep = allSteps.get(2);

        assertEquals(PolicyExecutionStepStatus.PENDING, requeuedStep.getStatus());
        assertEquals(now, requeuedStep.getDueAt());
        assertEquals(1, requeuedStep.getStaleRecoveryCount());

        assertEquals(PolicyExecutionStepStatus.FAILED, failedStep.getStatus());
        assertEquals(1, failedStep.getStaleRecoveryCount());
        assertTrue(failedStep.getErrorMessage().contains("Stale processing timeout"));

        assertEquals(PolicyExecutionStepStatus.PROCESSING, freshStep.getStatus());
    }

    @Test
    void shouldNotReturnOverlappingRowsAcrossConcurrentStaleRecoveryCalls() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-08T13:00:00Z");
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(newRun("IDEMP-D"));

        stepRepository.saveAndFlush(step(run.getId(), 1, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(25)));
        stepRepository.saveAndFlush(step(run.getId(), 2, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(24)));
        stepRepository.saveAndFlush(step(run.getId(), 3, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(23)));
        stepRepository.saveAndFlush(step(run.getId(), 4, PolicyExecutionStepStatus.PROCESSING, now.minusMinutes(22)));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<StaleRecoveryRow>> task = () -> claimRepository.recoverStaleProcessingSteps(
                    now.minusMinutes(15),
                    2,
                    1,
                    now);
            Future<List<StaleRecoveryRow>> first = executor.submit(task);
            Future<List<StaleRecoveryRow>> second = executor.submit(task);

            List<StaleRecoveryRow> firstResult = first.get();
            List<StaleRecoveryRow> secondResult = second.get();

            Set<Long> recoveredIds = new HashSet<>();
            firstResult.forEach(row -> recoveredIds.add(row.id()));
            secondResult.forEach(row -> recoveredIds.add(row.id()));

            assertEquals(4, recoveredIds.size());
            assertTrue(firstResult.stream().allMatch(row -> row.outcome() == StaleRecoveryOutcome.REQUEUED));
            assertTrue(secondResult.stream().allMatch(row -> row.outcome() == StaleRecoveryOutcome.REQUEUED));
        } catch (ExecutionException ex) {
            throw new RuntimeException("Concurrent stale recovery execution failed", ex);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private PolicyExecutionRunEntity newRun(String idempotencyKey) {
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setSource(WebhookSource.FUB);
        run.setEventId("evt-" + idempotencyKey);
        run.setWebhookEventId(null);
        run.setSourceLeadId("lead-123");
        run.setDomain("ASSIGNMENT");
        run.setPolicyKey("FOLLOW_UP_SLA");
        run.setPolicyVersion(1L);
        run.setPolicyBlueprintSnapshot(java.util.Map.of("templateKey", "assignment_followup_sla_v1"));
        run.setStatus(PolicyExecutionRunStatus.PENDING);
        run.setReasonCode(null);
        run.setIdempotencyKey(idempotencyKey);
        return run;
    }

    private PolicyExecutionStepEntity step(
            Long runId,
            int stepOrder,
            PolicyExecutionStepStatus status,
            OffsetDateTime dueAt) {
        PolicyExecutionStepEntity step = new PolicyExecutionStepEntity();
        step.setRunId(runId);
        step.setStepOrder(stepOrder);
        step.setStepType(PolicyStepType.WAIT_AND_CHECK_CLAIM);
        step.setStatus(status);
        step.setDueAt(dueAt);
        step.setDependsOnStepOrder(null);
        step.setResultCode(null);
        step.setErrorMessage(null);
        step.setStaleRecoveryCount(0);
        return step;
    }
}
