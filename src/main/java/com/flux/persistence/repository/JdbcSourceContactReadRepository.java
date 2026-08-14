package com.flux.persistence.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSourceContactReadRepository implements SourceContactReadRepository {

    /**
     * Deliberately one pass: build the arrival cohort, collect the leads reached and
     * the leads merely dialled as two set-valued CTEs, then join once and aggregate.
     *
     * <p>The obvious alternative — a per-lead subquery asking "was this one reached?" —
     * measured 112ms against 13ms for this shape at 20k leads, because it re-scans the
     * holder-intervals view once per lead. Keep it a join, never a lookup.
     *
     * <p>A call only counts for its lead's holder: it must fall inside that holder's
     * ownership interval AND have been dialled by them. Someone else's conversation is
     * real work, but it is not this agent's credit.
     */
    private static final String COUNT_BY_SOURCE_AGENT_STATE = """
            WITH cohort AS (
                SELECT p.source_person_id,
                       p.person_details ->> 'source'                     AS raw_source,
                       (p.person_details ->> 'assignedUserId')::bigint   AS agent_id,
                       p.person_details ->> 'assignedTo'                 AS agent_name,
                       p.person_details ->> 'contacted'                  AS contacted_flag
                  FROM persons p
                 WHERE p.kind = 'LEAD'
                   AND p.status = 'ACTIVE'
                   AND p.person_details ->> 'assignedUserId' IS NOT NULL
                   AND p.created_at >= :from
                   AND p.created_at <  :to
            ),
            calls AS (
                SELECT pc.source_person_id,
                       bool_or(pc.duration_seconds > :threshold) AS spoke
                  FROM processed_calls pc
                  JOIN v_lead_holder_intervals hi
                    ON hi.source_person_id = pc.source_person_id
                   AND pc.call_started_at >= hi.valid_from
                   AND pc.call_started_at <  hi.valid_to
                 WHERE pc.is_incoming = false
                   AND pc.source_person_id <> '0'
                   AND pc.source_user_id = hi.assigned_uid
                   AND pc.call_started_at < :to
                 GROUP BY pc.source_person_id
            )
            SELECT c.raw_source,
                   c.agent_id,
                   c.agent_name,
                   CASE
                       WHEN calls.spoke THEN 'SPOKE'
                       WHEN calls.source_person_id IS NOT NULL OR c.contacted_flag = '1' THEN 'ATTEMPTED'
                       ELSE 'NOTHING'
                   END AS state,
                   count(*) AS leads
              FROM cohort c
              LEFT JOIN calls ON calls.source_person_id = c.source_person_id
             GROUP BY c.raw_source, c.agent_id, c.agent_name, state
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSourceContactReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SourceContactRow> countBySourceAgentAndState(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds) {
        MapSqlParameterSource params = rangeParams(from, to)
                .addValue("threshold", conversationThresholdSeconds, Types.INTEGER);
        return jdbcTemplate.query(COUNT_BY_SOURCE_AGENT_STATE, params, (rs, rowNum) -> new SourceContactRow(
                rs.getString("raw_source"),
                rs.getObject("agent_id", Long.class),
                rs.getString("agent_name"),
                rs.getString("state"),
                rs.getLong("leads")));
    }

    @Override
    public long countEventsReceived(OffsetDateTime from, OffsetDateTime to) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM webhook_events WHERE received_at >= :from AND received_at < :to",
                rangeParams(from, to), Long.class);
        return count == null ? 0L : count;
    }

    private MapSqlParameterSource rangeParams(OffsetDateTime from, OffsetDateTime to) {
        return new MapSqlParameterSource()
                .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE);
    }
}
