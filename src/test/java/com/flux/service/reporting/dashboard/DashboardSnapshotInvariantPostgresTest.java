package com.flux.service.reporting.dashboard;

import com.flux.controller.dto.DashboardSnapshotDto;
import com.flux.controller.dto.DashboardSnapshotDto.HealthState;
import com.flux.persistence.entity.WorkflowRunStatus;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DashboardSnapshotInvariantPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("flux")
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
        registry.add("workflow.worker.enabled", () -> "false");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-02-01T10:30:00Z"), ZoneOffset.UTC);
        }
    }

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-01-31T10:00:00Z");

    @Autowired
    private DashboardSnapshotService service;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();
    private long workflowId;

    @BeforeEach
    void resetAndSeedParent() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE workflow_runs, events, webhook_events, automation_workflows RESTART IDENTITY CASCADE");
        workflowId = insertWorkflow();
    }

    @Test
    void seededSnapshot_holdsTheThreeWayInvariant() {
        for (int i = 0; i < 18; i++) {
            insertRun(WorkflowRunStatus.COMPLETED, FROM.plusHours(2));
        }
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(3));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(3));
        insertRun(WorkflowRunStatus.DUPLICATE_IGNORED, FROM.plusHours(4));

        DashboardSnapshotDto dto = service.snapshot();

        assertThat(dto.hero().openFailures()).isEqualTo(2L);
        assertThat(dto.funnel().failed().value()).isEqualTo(2L);
        assertThat(dto.needsAttention()).hasSize(2);
        assertThat(dto.hero().stats().runs().value()).isEqualTo(20L); // excludes DUPLICATE_IGNORED
        assertThat(dto.hero().stats().successRate().value()).isEqualTo(90.0);
        assertThat(dto.hero().state()).isEqualTo(HealthState.DEGRADED);
    }

    @Test
    void invariantHolds_underConcurrentWriter() throws InterruptedException {
        AtomicReference<Throwable> writerError = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 40; i++) {
                    insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(1)); // auto-commits in this thread
                    Thread.sleep(2);
                }
            } catch (Throwable t) {
                writerError.set(t);
            }
        });

        writer.start();
        for (int i = 0; i < 60; i++) {
            DashboardSnapshotDto dto = service.snapshot();
            long openFailures = dto.hero().openFailures();
            assertThat(dto.funnel().failed().value()).isEqualTo(openFailures);
            // openFailures (runStatusCounts) and the worklist (failedRuns) are two separate queries;
            // under REPEATABLE_READ they must agree within a single snapshot (total stays < the cap of 50).
            assertThat((long) dto.needsAttention().size()).isEqualTo(openFailures);
            Thread.sleep(1);
        }
        writer.join(10_000);
        assertThat(writerError.get()).isNull();
    }

    private long insertWorkflow() {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("key", "wf-dash").addValue("name", "wf-dash");
        return jdbc.queryForObject("""
                INSERT INTO automation_workflows (key, name, graph, status)
                VALUES (:key, :name, '{}'::jsonb, 'ACTIVE')
                RETURNING id
                """, p, Long.class);
    }

    private void insertRun(WorkflowRunStatus status, OffsetDateTime createdAt) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("wfId", workflowId, Types.BIGINT)
                .addValue("status", status.name())
                .addValue("idem", "idem-" + seq.incrementAndGet())
                .addValue("createdAt", createdAt, Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update("""
                INSERT INTO workflow_runs
                    (workflow_id, workflow_key, workflow_version, workflow_graph_snapshot,
                     source, idempotency_key, status, created_at, updated_at)
                VALUES
                    (:wfId, 'wf-dash', 1, '{}'::jsonb, 'FUB', :idem, :status, :createdAt, :createdAt)
                """, p);
    }
}
