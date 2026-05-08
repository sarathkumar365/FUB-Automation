package com.fuba.automation_engine.integration;

import com.fuba.automation_engine.persistence.entity.AutomationWorkflowEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepEntity;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowStatus;
import com.fuba.automation_engine.persistence.repository.AutomationWorkflowRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepClaimRepository.ClaimedStepRow;
import com.fuba.automation_engine.persistence.repository.WorkflowRunStepRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkflowRunStepClaimRepositoryPostgresTest {

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
    private WorkflowRunStepClaimRepository claimRepository;

    @Autowired
    private WorkflowRunRepository runRepository;

    @Autowired
    private WorkflowRunStepRepository stepRepository;

    @Autowired
    private AutomationWorkflowRepository workflowRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    void claimShouldSkipStepsWhoseParentRunIsCanceled() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-15T10:30:00Z");
        AutomationWorkflowEntity workflow = workflowRepository.saveAndFlush(workflow());

        WorkflowRunEntity pendingRun = runRepository.saveAndFlush(run(workflow.getId(), "IDEMP-P", WorkflowRunStatus.PENDING));
        WorkflowRunEntity canceledRun = runRepository.saveAndFlush(run(workflow.getId(), "IDEMP-C", WorkflowRunStatus.CANCELED));

        stepRepository.saveAndFlush(step(pendingRun.getId(), "n1", WorkflowRunStepStatus.PENDING, now.minusMinutes(2)));
        stepRepository.saveAndFlush(step(canceledRun.getId(), "n2", WorkflowRunStepStatus.PENDING, now.minusMinutes(1)));

        List<ClaimedStepRow> claimed = claimRepository.claimDuePendingSteps(now, 10);

        assertEquals(1, claimed.size());
        assertEquals(pendingRun.getId(), claimed.get(0).runId());
        assertEquals(WorkflowRunStepStatus.PROCESSING, claimed.get(0).status());
    }

    @Test
    void recoverStaleShouldSkipProcessingStepsWhoseParentRunIsCanceled() {
        OffsetDateTime now = OffsetDateTime.parse("2026-04-15T11:00:00Z");
        AutomationWorkflowEntity workflow = workflowRepository.saveAndFlush(workflow());

        WorkflowRunEntity pendingRun = runRepository.saveAndFlush(run(workflow.getId(), "IDEMP-SP", WorkflowRunStatus.PENDING));
        WorkflowRunEntity canceledRun = runRepository.saveAndFlush(run(workflow.getId(), "IDEMP-SC", WorkflowRunStatus.CANCELED));

        WorkflowRunStepEntity pendingStep = stepRepository.saveAndFlush(
                step(pendingRun.getId(), "np", WorkflowRunStepStatus.PROCESSING, now.minusMinutes(10)));
        WorkflowRunStepEntity canceledStep = stepRepository.saveAndFlush(
                step(canceledRun.getId(), "nc", WorkflowRunStepStatus.PROCESSING, now.minusMinutes(10)));

        backdateUpdatedAt(pendingStep.getId(), now.minusMinutes(10));
        backdateUpdatedAt(canceledStep.getId(), now.minusMinutes(10));

        List<WorkflowRunStepClaimRepository.StaleRecoveryRow> recovered = claimRepository.recoverStaleProcessingSteps(
                now.minusMinutes(5),
                10,
                1,
                now);

        assertEquals(1, recovered.size());
        assertEquals(pendingStep.getId(), recovered.get(0).id());
    }

    private AutomationWorkflowEntity workflow() {
        AutomationWorkflowEntity workflow = new AutomationWorkflowEntity();
        workflow.setKey("WF_CLAIM_GATE");
        workflow.setName("Workflow Claim Gate");
        workflow.setDescription("test");
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setVersionNumber(1);
        workflow.setGraph(java.util.Map.of(
                "schemaVersion", 1,
                "entryNode", "n1",
                "nodes", java.util.List.of(java.util.Map.of(
                        "id", "n1",
                        "type", "delay",
                        "config", java.util.Map.of("delayMinutes", 0),
                        "transitions", java.util.Map.of("DONE", java.util.Map.of("terminal", "COMPLETED"))))));
        return workflow;
    }

    private WorkflowRunEntity run(Long workflowId, String idempotencyKey, WorkflowRunStatus status) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowId(workflowId);
        run.setWorkflowKey("WF_CLAIM_GATE");
        run.setWorkflowVersion(1L);
        run.setWorkflowGraphSnapshot(java.util.Map.of(
                "schemaVersion", 1,
                "entryNode", "n1",
                "nodes", java.util.List.of()));
        run.setSource("FUB");
        run.setEventId("evt-" + idempotencyKey);
        run.setStatus(status);
        run.setIdempotencyKey(idempotencyKey);
        return run;
    }

    private WorkflowRunStepEntity step(Long runId, String nodeId, WorkflowRunStepStatus status, OffsetDateTime dueAt) {
        WorkflowRunStepEntity step = new WorkflowRunStepEntity();
        step.setRunId(runId);
        step.setNodeId(nodeId);
        step.setStepType("delay");
        step.setStatus(status);
        step.setDueAt(dueAt);
        step.setPendingDependencyCount(0);
        return step;
    }

    private void backdateUpdatedAt(Long stepId, OffsetDateTime timestamp) {
        jdbcTemplate.update(
                "UPDATE workflow_run_steps SET updated_at = :updatedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("updatedAt", timestamp)
                        .addValue("id", stepId));
    }
}
