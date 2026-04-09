package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.PolicyExecutionStepStatus;
import com.fuba.automation_engine.service.policy.PolicyStepType;
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
public class JdbcPolicyExecutionStepClaimRepository implements PolicyExecutionStepClaimRepository {

    private static final String CLAIM_SQL = """
            WITH due AS (
                SELECT id
                FROM policy_execution_steps
                WHERE status = :pendingStatus
                  AND due_at <= :now
                ORDER BY due_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE policy_execution_steps steps
            SET status = :processingStatus,
                updated_at = :now
            FROM due
            WHERE steps.id = due.id
            RETURNING
                steps.id,
                steps.run_id,
                steps.step_type,
                steps.step_order,
                steps.due_at,
                steps.status
            """;
    private static final String STALE_ERROR_MESSAGE = "Stale processing timeout; exceeded recovery limit";
    // TODO(known-issues#8): Prevent mixed outcomes within a run (requeued + failed rows in same recovery pass).
    private static final String RECOVER_STALE_SQL = """
            WITH stale AS (
                SELECT id
                FROM policy_execution_steps
                WHERE status = :processingStatus
                  AND updated_at <= :staleBefore
                ORDER BY updated_at, id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            UPDATE policy_execution_steps steps
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
                steps.step_type,
                steps.step_order,
                steps.status,
                steps.stale_recovery_count
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPolicyExecutionStepClaimRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ClaimedStepRow> claimDuePendingSteps(OffsetDateTime now, int limit) {
        int effectiveLimit = Math.max(1, limit);
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("pendingStatus", PolicyExecutionStepStatus.PENDING.name(), Types.VARCHAR)
                .addValue("processingStatus", PolicyExecutionStepStatus.PROCESSING.name(), Types.VARCHAR)
                .addValue("now", effectiveNow, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("limit", effectiveLimit, Types.INTEGER);

        return jdbcTemplate.query(CLAIM_SQL, params, rowMapper());
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
        OffsetDateTime effectiveStaleBefore = staleBefore == null
                ? effectiveNow
                : staleBefore;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("processingStatus", PolicyExecutionStepStatus.PROCESSING.name(), Types.VARCHAR)
                .addValue("pendingStatus", PolicyExecutionStepStatus.PENDING.name(), Types.VARCHAR)
                .addValue("failedStatus", PolicyExecutionStepStatus.FAILED.name(), Types.VARCHAR)
                .addValue("staleBefore", effectiveStaleBefore, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("limit", effectiveLimit, Types.INTEGER)
                .addValue("requeueLimit", effectiveRequeueLimit, Types.INTEGER)
                .addValue("staleErrorMessage", STALE_ERROR_MESSAGE, Types.VARCHAR)
                .addValue("now", effectiveNow, Types.TIMESTAMP_WITH_TIMEZONE);

        return jdbcTemplate.query(RECOVER_STALE_SQL, params, staleRecoveryRowMapper());
    }

    private RowMapper<ClaimedStepRow> rowMapper() {
        return (rs, rowNum) -> new ClaimedStepRow(
                rs.getLong("id"),
                rs.getLong("run_id"),
                PolicyStepType.valueOf(rs.getString("step_type")),
                rs.getInt("step_order"),
                getOffsetDateTime(rs),
                PolicyExecutionStepStatus.valueOf(rs.getString("status")));
    }

    private RowMapper<StaleRecoveryRow> staleRecoveryRowMapper() {
        return (rs, rowNum) -> {
            PolicyExecutionStepStatus status = PolicyExecutionStepStatus.valueOf(rs.getString("status"));
            StaleRecoveryOutcome outcome = status == PolicyExecutionStepStatus.PENDING
                    ? StaleRecoveryOutcome.REQUEUED
                    : StaleRecoveryOutcome.FAILED;
            return new StaleRecoveryRow(
                    rs.getLong("id"),
                    rs.getLong("run_id"),
                    PolicyStepType.valueOf(rs.getString("step_type")),
                    rs.getInt("step_order"),
                    status,
                    rs.getInt("stale_recovery_count"),
                    outcome);
        };
    }

    private OffsetDateTime getOffsetDateTime(ResultSet rs) throws SQLException {
        OffsetDateTime value = rs.getObject("due_at", OffsetDateTime.class);
        if (value != null) {
            return value;
        }
        java.sql.Timestamp timestamp = rs.getTimestamp("due_at");
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}
