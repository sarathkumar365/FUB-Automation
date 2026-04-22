package com.fuba.automation_engine.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.LeadFeedReadRepository.LeadFeedRow;
import java.nio.charset.StandardCharsets;
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

/**
 * JDBC implementation of {@link LeadFeedReadRepository}.
 *
 * <p>Orders by <code>updated_at DESC, id DESC</code> to keep "most recently
 * touched leads first" stable under ties. Keyset cursor condition is the
 * canonical <code>(updated_at, id) &lt; (cursor_updated_at, cursor_id)</code>
 * compound comparison expanded into the {@code OR} form so Postgres can use
 * {@code idx_leads_source_system_status_updated_at}.
 *
 * <p>The shape mirrors {@code JdbcWebhookFeedReadRepository}; intentional
 * duplication rather than sharing, because the key names, filter set, and
 * domain exception are different.
 */
@Repository
public class JdbcLeadFeedReadRepository implements LeadFeedReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcLeadFeedReadRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<LeadFeedRow> fetch(LeadFeedReadQuery query) {
        if ((query.cursorUpdatedAt() == null) != (query.cursorId() == null)) {
            throw new IllegalArgumentException("cursorUpdatedAt and cursorId must be provided together");
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, source_system, source_lead_id, status, lead_details,
                       created_at, updated_at, last_synced_at
                  FROM leads
                 WHERE 1=1
                """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (query.sourceSystem() != null && !query.sourceSystem().isBlank()) {
            sql.append(" AND source_system = :sourceSystem");
            params.addValue("sourceSystem", query.sourceSystem(), Types.VARCHAR);
        }
        if (query.status() != null) {
            sql.append(" AND status = :status");
            params.addValue("status", query.status().name(), Types.VARCHAR);
        }
        if (query.sourceLeadIdPrefix() != null && !query.sourceLeadIdPrefix().isBlank()) {
            sql.append(" AND source_lead_id LIKE :sourceLeadIdPrefix");
            params.addValue("sourceLeadIdPrefix", query.sourceLeadIdPrefix() + "%", Types.VARCHAR);
        }
        if (query.from() != null) {
            sql.append(" AND updated_at >= :from");
            params.addValue("from", query.from(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (query.to() != null) {
            sql.append(" AND updated_at <= :to");
            params.addValue("to", query.to(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (query.cursorUpdatedAt() != null && query.cursorId() != null) {
            sql.append(" AND (updated_at < :cursorUpdatedAt");
            sql.append(" OR (updated_at = :cursorUpdatedAt AND id < :cursorId))");
            params.addValue("cursorUpdatedAt", query.cursorUpdatedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            params.addValue("cursorId", query.cursorId(), Types.BIGINT);
        }

        sql.append(" ORDER BY updated_at DESC, id DESC");
        sql.append(" LIMIT :limit");
        params.addValue("limit", query.limit(), Types.INTEGER);

        return jdbcTemplate.query(sql.toString(), params, rowMapper());
    }

    private RowMapper<LeadFeedRow> rowMapper() {
        return (rs, rowNum) -> new LeadFeedRow(
                rs.getLong("id"),
                rs.getString("source_system"),
                rs.getString("source_lead_id"),
                LeadStatus.valueOf(rs.getString("status")),
                readJson(rs, "lead_details"),
                getOffsetDateTime(rs, "created_at"),
                getOffsetDateTime(rs, "updated_at"),
                getOffsetDateTime(rs, "last_synced_at"));
    }

    private OffsetDateTime getOffsetDateTime(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        if (value != null) {
            return value;
        }
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private JsonNode readJson(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) {
            return null;
        }
        try {
            if (raw instanceof String text) {
                return objectMapper.readTree(text);
            }
            if (raw instanceof byte[] bytes) {
                return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            }
            if ("org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
                String value = (String) raw.getClass().getMethod("getValue").invoke(raw);
                return value == null ? null : objectMapper.readTree(value);
            }
            return objectMapper.valueToTree(raw);
        } catch (Exception ex) {
            throw new SQLException("Failed to parse JSON column " + column, ex);
        }
    }
}
