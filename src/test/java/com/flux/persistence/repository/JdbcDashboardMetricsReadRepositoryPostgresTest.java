package com.flux.persistence.repository;

import com.flux.persistence.entity.WorkflowRunStatus;
import com.flux.persistence.repository.DashboardMetricsReadRepository.FailedRunRow;
import com.flux.persistence.repository.DashboardMetricsReadRepository.HourlyBucket;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcDashboardMetricsReadRepositoryPostgresTest {

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

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    private static final OffsetDateTime TO = FROM.plusHours(24);

    @Autowired
    private DashboardMetricsReadRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private final AtomicLong seq = new AtomicLong();
    private long workflowId;

    @BeforeEach
    void resetAndSeedParent() {
        jdbc.getJdbcOperations().update(
                "TRUNCATE workflow_runs, events, webhook_events, automation_workflows RESTART IDENTITY CASCADE");
        workflowId = insertWorkflow("wf-dash");
    }

    @Test
    void runStatusCounts_countsByStatus_overHalfOpenWindow() {
        insertRun(WorkflowRunStatus.COMPLETED, FROM); // inclusive lower bound
        insertRun(WorkflowRunStatus.COMPLETED, FROM.plusHours(1));
        insertRun(WorkflowRunStatus.COMPLETED, FROM.plusHours(5));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(2));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(23));
        insertRun(WorkflowRunStatus.DUPLICATE_IGNORED, FROM.plusHours(3));
        insertRun(WorkflowRunStatus.PENDING, FROM.plusHours(4));
        insertRun(WorkflowRunStatus.BLOCKED, FROM.plusHours(6));
        insertRun(WorkflowRunStatus.CANCELED, FROM.plusHours(7));
        insertRun(WorkflowRunStatus.COMPLETED, FROM.minusSeconds(1));
        insertRun(WorkflowRunStatus.FAILED, TO); // exclusive upper bound

        Map<WorkflowRunStatus, Long> counts = repository.runStatusCounts(FROM, TO);

        assertThat(counts).containsOnly(
                Map.entry(WorkflowRunStatus.COMPLETED, 3L),
                Map.entry(WorkflowRunStatus.FAILED, 2L),
                Map.entry(WorkflowRunStatus.DUPLICATE_IGNORED, 1L),
                Map.entry(WorkflowRunStatus.PENDING, 1L),
                Map.entry(WorkflowRunStatus.BLOCKED, 1L),
                Map.entry(WorkflowRunStatus.CANCELED, 1L));
    }

    @Test
    void hourlyFailedRuns_bucketsByUtcHour_truncatingMinutes() {
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(2).plusMinutes(15));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(2).plusMinutes(45));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(5).plusMinutes(30));
        insertRun(WorkflowRunStatus.COMPLETED, FROM.plusHours(2));

        List<HourlyBucket> buckets = repository.hourlyFailedRuns(FROM, TO);

        assertThat(buckets).containsExactly(
                new HourlyBucket(FROM.plusHours(2), 2L),
                new HourlyBucket(FROM.plusHours(5), 1L));
    }

    @Test
    void hourlyRunsTotal_countsAllStatusesExceptDuplicateIgnored() {
        insertRun(WorkflowRunStatus.COMPLETED, FROM.plusHours(1).plusMinutes(5));
        insertRun(WorkflowRunStatus.FAILED, FROM.plusHours(1).plusMinutes(10));
        insertRun(WorkflowRunStatus.PENDING, FROM.plusHours(1).plusMinutes(15));
        insertRun(WorkflowRunStatus.BLOCKED, FROM.plusHours(1).plusMinutes(20));
        insertRun(WorkflowRunStatus.CANCELED, FROM.plusHours(1).plusMinutes(25));
        insertRun(WorkflowRunStatus.DUPLICATE_IGNORED, FROM.plusHours(1).plusMinutes(30));

        List<HourlyBucket> buckets = repository.hourlyRunsTotal(FROM, TO);

        assertThat(buckets).containsExactly(new HourlyBucket(FROM.plusHours(1), 5L));
    }

    @Test
    void hourlyIngested_andCountSince_overWebhookEvents() {
        insertWebhook(FROM.plusHours(1).plusMinutes(10));
        insertWebhook(FROM.plusHours(1).plusMinutes(50));
        insertWebhook(FROM.plusHours(3));

        assertThat(repository.hourlyIngested(FROM, TO)).containsExactly(
                new HourlyBucket(FROM.plusHours(1), 2L),
                new HourlyBucket(FROM.plusHours(3), 1L));

        assertThat(repository.countWebhookEventsSince(FROM.plusHours(3))).isEqualTo(1L);
        assertThat(repository.countWebhookEventsSince(FROM.plusHours(2))).isEqualTo(1L);
    }

    @Test
    void hourlyDomainEvents_countsEventsTable() {
        insertEvent(FROM.plusHours(10).plusMinutes(5));
        insertEvent(FROM.plusHours(10).plusMinutes(40));

        assertThat(repository.hourlyDomainEvents(FROM, TO))
                .containsExactly(new HourlyBucket(FROM.plusHours(10), 2L));
    }

    @Test
    void failedRuns_newestFirst_respectsLimit_andNullReason() {
        long oldest = insertFailedRun(FROM.plusHours(2), "REASON_A");
        long middle = insertFailedRun(FROM.plusHours(4), null);
        long newest = insertFailedRun(FROM.plusHours(6), "REASON_C");

        List<FailedRunRow> rows = repository.failedRuns(FROM, TO, 2);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).id()).isEqualTo(newest);
        assertThat(rows.get(0).reasonCode()).isEqualTo("REASON_C");
        assertThat(rows.get(0).createdAt()).isEqualTo(FROM.plusHours(6));
        assertThat(rows.get(1).id()).isEqualTo(middle);
        assertThat(rows.get(1).reasonCode()).isNull();
        assertThat(rows).noneMatch(r -> r.id() == oldest);
    }

    private long insertWorkflow(String key) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("name", key);
        return jdbc.queryForObject("""
                INSERT INTO automation_workflows (key, name, graph, status)
                VALUES (:key, :name, '{}'::jsonb, 'ACTIVE')
                RETURNING id
                """, p, Long.class);
    }

    private long insertRun(WorkflowRunStatus status, OffsetDateTime createdAt) {
        long n = seq.incrementAndGet();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("wfId", workflowId, Types.BIGINT)
                .addValue("status", status.name())
                .addValue("idem", "idem-" + n)
                .addValue("createdAt", createdAt, Types.TIMESTAMP_WITH_TIMEZONE);
        return jdbc.queryForObject("""
                INSERT INTO workflow_runs
                    (workflow_id, workflow_key, workflow_version, workflow_graph_snapshot,
                     source, idempotency_key, status, created_at, updated_at)
                VALUES
                    (:wfId, 'wf-dash', 1, '{}'::jsonb,
                     'FUB', :idem, :status, :createdAt, :createdAt)
                RETURNING id
                """, p, Long.class);
    }

    private long insertFailedRun(OffsetDateTime createdAt, String reasonCode) {
        long id = insertRun(WorkflowRunStatus.FAILED, createdAt);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id, Types.BIGINT)
                .addValue("reason", reasonCode, Types.VARCHAR);
        jdbc.update("UPDATE workflow_runs SET reason_code = :reason WHERE id = :id", p);
        return id;
    }

    private void insertWebhook(OffsetDateTime receivedAt) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("eventId", "evt-" + seq.incrementAndGet())
                .addValue("receivedAt", receivedAt, Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update("""
                INSERT INTO webhook_events (source, event_id, status, payload, received_at)
                VALUES ('FUB', :eventId, 'RECEIVED', '{}'::jsonb, :receivedAt)
                """, p);
    }

    private void insertEvent(OffsetDateTime createdAt) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("createdAt", createdAt, Types.TIMESTAMP_WITH_TIMEZONE);
        jdbc.update("""
                INSERT INTO events (event_kind, source_system, entity_type, entity_id, payload, created_at)
                VALUES ('person.created', 'FUB', 'person', '1', '{}'::jsonb, :createdAt)
                """, p);
    }
}
