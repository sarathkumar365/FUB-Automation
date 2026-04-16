package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.WorkflowRunStepStatus;
import com.fuba.automation_engine.persistence.entity.WorkflowRunStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowRunStepClaimRepository implements WorkflowRunStepClaimRepository {

    private static final String CLAIM_SQL = """
            WITH due AS (
                SELECT steps.id
                FROM workflow_run_steps steps
                JOIN workflow_runs runs ON runs.id = steps.run_id
                WHERE steps.status = :pendingStatus
                  AND steps.due_at <= :now
                  AND runs.status = :runPendingStatus
                ORDER BY steps.due_at, steps.id
                LIMIT :limit
                FOR UPDATE OF steps SKIP LOCKED
            )
            UPDATE workflow_run_steps steps
            SET status = :processingStatus,
                updated_at = :now
            FROM due
            WHERE steps.id = due.id
            RETURNING
                steps.id,
                steps.run_id,
                steps.node_id,
                steps.step_type,
                steps.due_at,
                steps.status
            """;

    private static final String STALE_ERROR_MESSAGE = "Stale processing timeout; exceeded recovery limit";

    private static final String RECOVER_STALE_SQL = """
            WITH stale AS (
                SELECT steps.id
                FROM workflow_run_steps steps
                JOIN workflow_runs runs ON runs.id = steps.run_id
                WHERE steps.status = :processingStatus
                  AND steps.updated_at <= :staleBefore
                  AND runs.status = :runPendingStatus
                ORDER BY steps.updated_at, steps.id
                LIMIT :limit
                FOR UPDATE OF steps SKIP LOCKED
            )
            UPDATE workflow_run_steps steps
            SET status = CASE
                    WHEN steps.stale_recovery_count < :requeueLimit THEN :pendingStatus
                    ELSE :failedStatus
                END,
                due_at = CASE
                    WHEN steps.stale_recovery_count < :requeueLimit THEN :now
                    ELSE steps.due_at
                END,
                result_code = NULL,
                error_message = CASE
                    WHEN steps.stale_recovery_count < :requeueLimit THEN NULL
                    ELSE :staleErrorMessage
                END,
                stale_recovery_count = CASE
                    WHEN steps.stale_recovery_count < :requeueLimit THEN steps.stale_recovery_count + 1
                    ELSE steps.stale_recovery_count
                END,
                updated_at = :now
            FROM stale
            WHERE steps.id = stale.id
            RETURNING
                steps.id,
                steps.run_id,
                steps.node_id,
                steps.step_type,
                steps.status,
                steps.stale_recovery_count
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowRunStepClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ClaimedStepRow> claimDuePendingSteps(OffsetDateTime now, int limit) {
        int effectiveLimit = Math.max(1, limit);
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendingStatus", WorkflowRunStepStatus.PENDING.name(), Types.VARCHAR)
                .addValue("processingStatus", WorkflowRunStepStatus.PROCESSING.name(), Types.VARCHAR)
                .addValue("runPendingStatus", WorkflowRunStatus.PENDING.name(), Types.VARCHAR)
                .addValue("now", effectiveNow, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("limit", effectiveLimit, Types.INTEGER);

        return jdbcTemplate.query(CLAIM_SQL, params, claimedStepRowMapper());
    }

    @Override
    public List<StaleRecoveryRow> recoverStaleProcessingSteps(
            OffsetDateTime staleBefore,
            int limit,
            int requeueLimit,
            OffsetDateTime now) {
        int effectiveLimit = Math.max(1, limit);
        int effectiveRequeueLimit = Math.max(0, requeueLimit);
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        OffsetDateTime effectiveStaleBefore = staleBefore == null ? effectiveNow : staleBefore;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("processingStatus", WorkflowRunStepStatus.PROCESSING.name(), Types.VARCHAR)
                .addValue("pendingStatus", WorkflowRunStepStatus.PENDING.name(), Types.VARCHAR)
                .addValue("failedStatus", WorkflowRunStepStatus.FAILED.name(), Types.VARCHAR)
                .addValue("runPendingStatus", WorkflowRunStatus.PENDING.name(), Types.VARCHAR)
                .addValue("staleBefore", effectiveStaleBefore, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("limit", effectiveLimit, Types.INTEGER)
                .addValue("requeueLimit", effectiveRequeueLimit, Types.INTEGER)
                .addValue("staleErrorMessage", STALE_ERROR_MESSAGE, Types.VARCHAR)
                .addValue("now", effectiveNow, Types.TIMESTAMP_WITH_TIMEZONE);

        return jdbcTemplate.query(RECOVER_STALE_SQL, params, staleRecoveryRowMapper());
    }

    private RowMapper<ClaimedStepRow> claimedStepRowMapper() {
        return (rs, rowNum) -> new ClaimedStepRow(
                rs.getLong("id"),
                rs.getLong("run_id"),
                rs.getString("node_id"),
                rs.getString("step_type"),
                getOffsetDateTime(rs, "due_at"),
                WorkflowRunStepStatus.valueOf(rs.getString("status")));
    }

    private RowMapper<StaleRecoveryRow> staleRecoveryRowMapper() {
        return (rs, rowNum) -> {
            WorkflowRunStepStatus status = WorkflowRunStepStatus.valueOf(rs.getString("status"));
            StaleRecoveryOutcome outcome = status == WorkflowRunStepStatus.PENDING
                    ? StaleRecoveryOutcome.REQUEUED
                    : StaleRecoveryOutcome.FAILED;
            return new StaleRecoveryRow(
                    rs.getLong("id"),
                    rs.getLong("run_id"),
                    rs.getString("node_id"),
                    rs.getString("step_type"),
                    status,
                    rs.getInt("stale_recovery_count"),
                    outcome);
        };
    }

    private OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        if (value != null) {
            return value;
        }
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}
