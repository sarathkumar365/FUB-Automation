package com.flux.persistence.repository;

import com.flux.persistence.entity.WorkflowRunStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDashboardMetricsReadRepository implements DashboardMetricsReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcDashboardMetricsReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<WorkflowRunStatus, Long> runStatusCounts(OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT status, COUNT(*) AS cnt
                  FROM workflow_runs
                 WHERE created_at >= :from AND created_at < :to
                 GROUP BY status
                """;
        Map<WorkflowRunStatus, Long> counts = new EnumMap<>(WorkflowRunStatus.class);
        jdbcTemplate.query(sql, rangeParams(from, to), (RowCallbackHandler) rs ->
                counts.put(WorkflowRunStatus.valueOf(rs.getString("status")), rs.getLong("cnt")));
        return counts;
    }

    @Override
    public List<HourlyBucket> hourlyIngested(OffsetDateTime from, OffsetDateTime to) {
        return hourlyBuckets("webhook_events", "received_at", null, from, to);
    }

    @Override
    public List<HourlyBucket> hourlyDomainEvents(OffsetDateTime from, OffsetDateTime to) {
        return hourlyBuckets("events", "created_at", null, from, to);
    }

    @Override
    public List<HourlyBucket> hourlyRunsTotal(OffsetDateTime from, OffsetDateTime to) {
        return hourlyBuckets("workflow_runs", "created_at", "status <> 'DUPLICATE_IGNORED'", from, to);
    }

    @Override
    public List<HourlyBucket> hourlyFailedRuns(OffsetDateTime from, OffsetDateTime to) {
        return hourlyBuckets("workflow_runs", "created_at", "status = 'FAILED'", from, to);
    }

    @Override
    public long countWebhookEventsSince(OffsetDateTime since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("since", since, Types.TIMESTAMP_WITH_TIMEZONE);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_events WHERE received_at >= :since", params, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public List<FailedRunRow> failedRuns(OffsetDateTime from, OffsetDateTime to, int limit) {
        String sql = """
                SELECT id, workflow_key, reason_code, created_at
                  FROM workflow_runs
                 WHERE status = 'FAILED' AND created_at >= :from AND created_at < :to
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit
                """;
        MapSqlParameterSource params = rangeParams(from, to).addValue("limit", limit, Types.INTEGER);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new FailedRunRow(
                rs.getLong("id"),
                rs.getString("workflow_key"),
                rs.getString("reason_code"),
                offsetDateTime(rs, "created_at")));
    }

    private List<HourlyBucket> hourlyBuckets(
            String table, String tsColumn, String extraPredicate, OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT date_trunc('hour', ").append(tsColumn).append(" AT TIME ZONE 'UTC') AS bucket,")
                .append(" COUNT(*) AS cnt FROM ").append(table)
                .append(" WHERE ").append(tsColumn).append(" >= :from AND ").append(tsColumn).append(" < :to");
        if (extraPredicate != null) {
            sql.append(" AND ").append(extraPredicate);
        }
        sql.append(" GROUP BY bucket ORDER BY bucket");
        return jdbcTemplate.query(sql.toString(), rangeParams(from, to), (rs, rowNum) -> {
            // date_trunc(... AT TIME ZONE 'UTC') yields a timestamp without time zone (UTC wall clock)
            LocalDateTime bucket = rs.getObject("bucket", LocalDateTime.class);
            return new HourlyBucket(bucket == null ? null : bucket.atOffset(ZoneOffset.UTC), rs.getLong("cnt"));
        });
    }

    private MapSqlParameterSource rangeParams(OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource()
                .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        if (value != null) {
            return value;
        }
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}
