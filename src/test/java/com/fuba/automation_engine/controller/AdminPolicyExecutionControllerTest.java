package com.fuba.automation_engine.controller;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPolicyExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void shouldReturnListWithFiltersAndCursor() throws Exception {
        PolicyExecutionRunEntity first = runRepository.saveAndFlush(buildRun(
                "evt-pe-1",
                "FOLLOW_UP_SLA",
                PolicyExecutionRunStatus.PENDING,
                OffsetDateTime.parse("2026-04-02T12:10:00Z")));
        runRepository.saveAndFlush(buildRun(
                "evt-pe-1b",
                "FOLLOW_UP_SLA",
                PolicyExecutionRunStatus.PENDING,
                OffsetDateTime.parse("2026-04-02T12:05:00Z")));
        runRepository.saveAndFlush(buildRun(
                "evt-pe-2",
                "FOLLOW_UP_SLA",
                PolicyExecutionRunStatus.BLOCKED_IDENTITY,
                OffsetDateTime.parse("2026-04-02T12:00:00Z")));
        runRepository.saveAndFlush(buildRun(
                "evt-pe-3",
                "OTHER_POLICY",
                PolicyExecutionRunStatus.PENDING,
                OffsetDateTime.parse("2026-04-02T11:50:00Z")));

        mockMvc.perform(get("/admin/policy-executions")
                        .param("status", "PENDING")
                        .param("policyKey", "follow_up_sla")
                        .param("from", "2026-04-02T12:00:00Z")
                        .param("to", "2026-04-02T12:20:00Z")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.getId()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.nextCursor").exists())
                .andExpect(jsonPath("$.serverTime").exists());
    }

    @Test
    void shouldReturnDetailWithOrderedSteps() throws Exception {
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(buildRun(
                "evt-pe-detail",
                "FOLLOW_UP_SLA",
                PolicyExecutionRunStatus.PENDING,
                OffsetDateTime.parse("2026-04-02T12:00:00Z")));

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
                OffsetDateTime.parse("2026-04-02T12:05:00Z"),
                null));

        mockMvc.perform(get("/admin/policy-executions/" + run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(run.getId()))
                .andExpect(jsonPath("$.policyKey").value("FOLLOW_UP_SLA"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].stepOrder").value(1))
                .andExpect(jsonPath("$.steps[1].stepOrder").value(2));
    }

    @Test
    void shouldReturnBadRequestForInvalidRange() throws Exception {
        mockMvc.perform(get("/admin/policy-executions")
                        .param("from", "2026-04-03T00:00:00Z")
                        .param("to", "2026-04-02T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForMissingDetail() throws Exception {
        mockMvc.perform(get("/admin/policy-executions/999999"))
                .andExpect(status().isNotFound());
    }

    private PolicyExecutionRunEntity buildRun(
            String eventId,
            String policyKey,
            PolicyExecutionRunStatus status,
            OffsetDateTime createdAt) {
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setSource(WebhookSource.FUB);
        run.setEventId(eventId);
        run.setWebhookEventId(null);
        run.setSourceLeadId("lead-" + eventId);
        run.setInternalLeadRef("internal-" + eventId);
        run.setDomain("ASSIGNMENT");
        run.setPolicyKey(policyKey);
        run.setPolicyVersion(1L);
        run.setPolicyBlueprintSnapshot(Map.of("templateKey", "assignment_followup_sla_v1"));
        run.setStatus(status);
        run.setReasonCode(null);
        run.setIdempotencyKey("idem-" + eventId);
        run.setCreatedAt(createdAt);
        run.setUpdatedAt(createdAt);
        return run;
    }

    private PolicyExecutionStepEntity buildStep(
            Long runId,
            int stepOrder,
            PolicyStepType stepType,
            PolicyExecutionStepStatus status,
            OffsetDateTime dueAt,
            Integer dependsOnStepOrder) {
        PolicyExecutionStepEntity step = new PolicyExecutionStepEntity();
        step.setRunId(runId);
        step.setStepOrder(stepOrder);
        step.setStepType(stepType);
        step.setStatus(status);
        step.setDueAt(dueAt);
        step.setDependsOnStepOrder(dependsOnStepOrder);
        return step;
    }
}
