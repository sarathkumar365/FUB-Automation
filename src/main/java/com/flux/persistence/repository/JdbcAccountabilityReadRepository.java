package com.flux.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountabilityReadRepository implements AccountabilityReadRepository {

    /**
     * The lead cohort, plus the single call that decides each lead's state.
     *
     * <p>{@code DISTINCT ON} with a conversation-first ordering picks the best call per
     * lead: a real conversation if one exists, otherwise the most recent dial. That call's
     * holder is the agent who gets the row, which is what makes this timeline-correct —
     * credit follows whoever owned the lead at the moment of the call, not whoever holds
     * it today.
     *
     * <p>Only the holder's own calls qualify (`source_user_id = hi.assigned_uid`). A
     * conversation by anyone else is real work but not this agent's credit, so it leaves
     * the lead unreached — deliberately, since the point of the board is who did the work.
     */
    private static final String COHORT_AND_DECIDING_CALL = """
            cohort AS (
                SELECT p.source_person_id,
                       (p.person_details ->> 'assignedUserId')::bigint AS current_uid,
                       p.person_details ->> 'contacted'                AS contacted_flag,
                       p.person_details ->> 'source'                   AS raw_source,
                       p.person_details ->> 'name'                     AS lead_name,
                       -- The primary handle, not merely the first: FUB's arrays are not
                       -- ordered, so taking [0] can hand an agent a landline to dial.
                       coalesce(
                           (SELECT e ->> 'value' FROM jsonb_array_elements(p.person_details -> 'phones') e
                             -- Compared as text, not cast to int: FUB sends this as a number
                             -- today, but a boolean would make a cast abort the whole query.
                             WHERE e ->> 'isPrimary' IN ('1', 'true') LIMIT 1),
                           p.person_details -> 'phones' -> 0 ->> 'value')  AS phone,
                       coalesce(
                           (SELECT e ->> 'value' FROM jsonb_array_elements(p.person_details -> 'emails') e
                             -- Compared as text, not cast to int: FUB sends this as a number
                             -- today, but a boolean would make a cast abort the whole query.
                             WHERE e ->> 'isPrimary' IN ('1', 'true') LIMIT 1),
                           p.person_details -> 'emails' -> 0 ->> 'value')  AS email,
                       p.created_at                                    AS arrived_at
                  FROM persons p
                 WHERE p.kind = 'LEAD'
                   AND p.status = 'ACTIVE'
                   AND p.person_details ->> 'assignedUserId' IS NOT NULL
                   AND p.created_at >= :from
                   AND p.created_at <  :to
            ),
            deciding_call AS (
                SELECT DISTINCT ON (pc.source_person_id)
                       pc.source_person_id,
                       hi.assigned_uid                          AS acting_uid,
                       pc.duration_seconds > :threshold         AS spoke
                  FROM processed_calls pc
                  JOIN v_lead_holder_intervals hi
                    ON hi.source_person_id = pc.source_person_id
                   AND pc.call_started_at >= hi.valid_from
                   AND pc.call_started_at <  hi.valid_to
                 WHERE pc.is_incoming = false
                   AND pc.source_person_id <> '0'
                   AND pc.source_user_id = hi.assigned_uid
                   AND pc.call_started_at < :to
                 ORDER BY pc.source_person_id,
                          -- NULLS LAST matters: Postgres sorts NULL first under DESC, so a
                          -- call with no recorded duration would otherwise be picked ahead
                          -- of a real conversation and the lead would read as merely dialled.
                          (pc.duration_seconds > :threshold) DESC NULLS LAST,
                          pc.call_started_at DESC
            ),
            attributed AS (
                SELECT c.*,
                       coalesce(d.acting_uid, c.current_uid) AS agent_id,
                       CASE
                           WHEN d.spoke THEN 'SPOKE'
                           WHEN d.source_person_id IS NOT NULL OR c.contacted_flag = '1' THEN 'ATTEMPTED'
                           ELSE 'NOTHING'
                       END AS state
                  FROM cohort c
                  LEFT JOIN deciding_call d ON d.source_person_id = c.source_person_id
            )
            """;

    /**
     * One display name per user id. FUB carries several spellings for the same person
     * ("Arjun Ahluwalia" / "Arjun Singh Ahluwalia"), so grouping by name would split an
     * agent in two; the most-used spelling wins, ties broken by name for determinism.
     */
    private static final String NAMES = """
            names AS (
                SELECT DISTINCT ON (uid) uid, nm FROM (
                    SELECT (person_details ->> 'assignedUserId')::bigint AS uid,
                           person_details ->> 'assignedTo'              AS nm,
                           count(*)                                     AS n
                      FROM persons
                     WHERE kind = 'LEAD'
                       AND person_details ->> 'assignedTo' IS NOT NULL
                     GROUP BY 1, 2
                ) v ORDER BY uid, n DESC, nm
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAccountabilityReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentStateRow> countByAgentAndState(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds) {
        String sql = "WITH " + COHORT_AND_DECIDING_CALL + ", " + NAMES + """
                SELECT a.agent_id, n.nm AS agent_name, a.state, count(*) AS leads
                  FROM attributed a
                  LEFT JOIN names n ON n.uid = a.agent_id
                 GROUP BY a.agent_id, n.nm, a.state
                """;
        return jdbcTemplate.query(sql, params(from, to, conversationThresholdSeconds),
                (rs, rowNum) -> new AgentStateRow(
                        rs.getObject("agent_id", Long.class),
                        rs.getString("agent_name"),
                        rs.getString("state"),
                        rs.getLong("leads")));
    }

    @Override
    public List<UnreachedLeadRow> unreachedLeads(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds, long agentId, int limit) {
        // Keyed on current_uid, not the attributed agent_id: the worklist is for whoever can
        // still pick up the phone. A lead dialled by its previous holder and then reassigned
        // belongs on the new holder's list, not stranded on the list of someone who no longer
        // owns it. Scoped to NOTHING so the list length equals the red number it drills into —
        // attempted-but-unspoken leads are counted separately on the board.
        String sql = "WITH " + COHORT_AND_DECIDING_CALL + """
                SELECT a.source_person_id, a.lead_name, a.phone, a.email, a.raw_source, a.arrived_at
                  FROM attributed a
                 WHERE a.current_uid = :agentId
                   AND a.state = 'NOTHING'
                 ORDER BY a.arrived_at, a.source_person_id
                 LIMIT :limit
                """;
        MapSqlParameterSource params = params(from, to, conversationThresholdSeconds)
                .addValue("agentId", agentId, Types.BIGINT)
                .addValue("limit", limit, Types.INTEGER);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new UnreachedLeadRow(
                rs.getString("source_person_id"),
                rs.getString("lead_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("raw_source"),
                offsetDateTime(rs, "arrived_at")));
    }

    @Override
    public String agentName(long agentId) {
        String sql = "WITH " + NAMES + " SELECT nm FROM names WHERE uid = :agentId";
        List<String> names = jdbcTemplate.queryForList(
                sql, new MapSqlParameterSource("agentId", agentId), String.class);
        return names.isEmpty() ? null : names.get(0);
    }

    private MapSqlParameterSource params(OffsetDateTime from, OffsetDateTime to, int threshold) {
        return new MapSqlParameterSource()
                .addValue("from", from, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", to, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("threshold", threshold, Types.INTEGER);
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
