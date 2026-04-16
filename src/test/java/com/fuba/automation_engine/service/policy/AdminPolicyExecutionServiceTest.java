package com.fuba.automation_engine.service.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.controller.dto.PolicyExecutionRunDetailResponse;
import com.fuba.automation_engine.controller.dto.PolicyExecutionRunPageResponse;
import com.fuba.automation_engine.exception.policy.InvalidPolicyExecutionQueryException;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionRunStatus;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepEntity;
import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionRunRepository;
import com.fuba.automation_engine.persistence.repository.PolicyExecutionStepRepository;
import com.fuba.automation_engine.service.policy.AdminPolicyExecutionService.PolicyExecutionFeedQuery;
import com.fuba.automation_engine.service.policy.PolicyStepType;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({
        AdminPolicyExecutionService.class,
        AdminPolicyExecutionServiceTest.TestConfig.class
})
class AdminPolicyExecutionServiceTest {

    @Autowired
    private PolicyExecutionRunRepository runRepository;

    @Autowired
    private PolicyExecutionStepRepository stepRepository;

    @Autowired
    private AdminPolicyExecutionService service;

    @BeforeEach
    void setUp() {
        stepRepository.deleteAll();
        runRepository.deleteAll();
    }

    @Test
    void shouldBuildPagedListWithCursor() {
        runRepository.saveAndFlush(run(300L, "FOLLOW_UP_SLA", OffsetDateTime.parse("2026-04-02T12:00:00Z")));
        runRepository.saveAndFlush(run(299L, "FOLLOW_UP_SLA", OffsetDateTime.parse("2026-04-02T11:00:00Z")));
        runRepository.saveAndFlush(run(298L, "FOLLOW_UP_SLA", OffsetDateTime.parse("2026-04-02T10:00:00Z")));

        PolicyExecutionRunPageResponse page = service.list(new PolicyExecutionFeedQuery(
                PolicyExecutionRunStatus.PENDING,
                "  follow_up_sla  ",
                null,
                null,
                2,
                null));

        assertEquals(2, page.items().size());
        assertEquals("evt-300", page.items().get(0).eventId());
        assertEquals("evt-299", page.items().get(1).eventId());
        assertTrue(page.items().get(0).createdAt().isAfter(page.items().get(1).createdAt()));
        assertNotNull(page.nextCursor());
        assertEquals(OffsetDateTime.parse("2026-04-02T20:00:00Z"), page.serverTime());
    }

    @Test
    void shouldListWithoutOptionalFilters() {
        runRepository.saveAndFlush(run(301L, "FOLLOW_UP_SLA", OffsetDateTime.parse("2026-04-02T12:01:00Z")));
        runRepository.saveAndFlush(run(300L, "FOLLOW_UP_SLA", OffsetDateTime.parse("2026-04-02T12:00:00Z")));

        PolicyExecutionRunPageResponse page = service.list(new PolicyExecutionFeedQuery(
                null,
                null,
                null,
                null,
                10,
                null));

        assertEquals(2, page.items().size());
        assertEquals("evt-301", page.items().get(0).eventId());
        assertEquals("evt-300", page.items().get(1).eventId());
        assertEquals(OffsetDateTime.parse("2026-04-02T20:00:00Z"), page.serverTime());
    }

    @Test
    void shouldRejectInvalidRange() {
        assertThrows(
                InvalidPolicyExecutionQueryException.class,
                () -> service.list(new PolicyExecutionFeedQuery(
                        null,
                        null,
                        OffsetDateTime.parse("2026-04-03T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-02T00:00:00Z"),
                        10,
                        null)));
    }

    @Test
    void shouldReturnRunDetailWithOrderedSteps() {
        PolicyExecutionRunEntity run = runRepository.saveAndFlush(run(
                501L,
                "FOLLOW_UP_SLA",
                OffsetDateTime.parse("2026-04-02T12:00:00Z")));
        stepRepository.saveAndFlush(step(run.getId(), 2, PolicyStepType.WAIT_AND_CHECK_COMMUNICATION, PolicyExecutionStepStatus.WAITING_DEPENDENCY));
        stepRepository.saveAndFlush(step(run.getId(), 1, PolicyStepType.WAIT_AND_CHECK_CLAIM, PolicyExecutionStepStatus.PENDING));

        PolicyExecutionRunDetailResponse detail = service.findDetail(run.getId()).orElseThrow();

        assertEquals(run.getId(), detail.id());
        assertEquals("FOLLOW_UP_SLA", detail.policyKey());
        assertEquals(2, detail.steps().size());
        assertEquals(1, detail.steps().get(0).stepOrder());
        assertEquals(2, detail.steps().get(1).stepOrder());
    }

    private PolicyExecutionRunEntity run(long suffix, String policyKey, OffsetDateTime createdAt) {
        PolicyExecutionRunEntity run = new PolicyExecutionRunEntity();
        run.setSource(WebhookSource.FUB);
        run.setEventId("evt-" + suffix);
        run.setSourceLeadId("lead-" + suffix);
        run.setDomain("ASSIGNMENT");
        run.setPolicyKey(policyKey);
        run.setPolicyVersion(1L);
        run.setPolicyBlueprintSnapshot(Map.of("templateKey", "assignment_followup_sla_v1"));
        run.setStatus(PolicyExecutionRunStatus.PENDING);
        run.setReasonCode(null);
        run.setIdempotencyKey("idem-" + suffix);
        run.setCreatedAt(createdAt);
        run.setUpdatedAt(createdAt);
        return run;
    }

    private PolicyExecutionStepEntity step(
            Long runId,
            int stepOrder,
            PolicyStepType stepType,
            PolicyExecutionStepStatus status) {
        PolicyExecutionStepEntity step = new PolicyExecutionStepEntity();
        step.setRunId(runId);
        step.setStepOrder(stepOrder);
        step.setStepType(stepType);
        step.setStatus(status);
        step.setDueAt(null);
        step.setDependsOnStepOrder(stepOrder == 1 ? null : stepOrder - 1);
        return step;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PolicyExecutionCursorCodec policyExecutionCursorCodec() {
            return new PolicyExecutionCursorCodec(new ObjectMapper());
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-04-02T20:00:00Z"), ZoneOffset.UTC);
        }
    }
}
