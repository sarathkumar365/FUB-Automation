package com.fuba.automation_engine.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedReadQuery;
import com.fuba.automation_engine.persistence.repository.WebhookFeedReadRepository.WebhookFeedRow;
import com.fuba.automation_engine.service.webhook.model.EventSupportState;
import com.fuba.automation_engine.service.webhook.model.NormalizedAction;
import com.fuba.automation_engine.service.webhook.model.NormalizedDomain;
import com.fuba.automation_engine.service.webhook.model.WebhookEventStatus;
import com.fuba.automation_engine.service.webhook.model.WebhookSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWebhookFeedReadRepository implements WebhookFeedReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWebhookFeedReadRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WebhookFeedRow> fetch(WebhookFeedReadQuery query) {
        if ((query.cursorReceivedAt() == null) != (query.cursorId() == null)) {
            throw new IllegalArgumentException("cursorReceivedAt and cursorId must be provided together");
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, event_id, source, event_type, catalog_state, normalized_domain, normalized_action, status, received_at
                """);
        if (query.includePayload()) {
            sql.append(", payload");
        }
        sql.append("""
                 FROM webhook_events
                 WHERE 1=1
                """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (query.source() != null) {
            sql.append(" AND source = :source");
            params.addValue("source", query.source().name(), Types.VARCHAR);
        }
        if (query.status() != null) {
            sql.append(" AND status = :status");
            params.addValue("status", query.status().name(), Types.VARCHAR);
        }
        if (query.eventType() != null && !query.eventType().isBlank()) {
            sql.append(" AND event_type = :eventType");
            params.addValue("eventType", query.eventType(), Types.VARCHAR);
        }
        if (query.from() != null) {
            sql.append(" AND received_at >= :from");
            params.addValue("from", query.from(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (query.to() != null) {
            sql.append(" AND received_at <= :to");
            params.addValue("to", query.to(), Types.TIMESTAMP_WITH_TIMEZONE);
        }
        if (query.cursorReceivedAt() != null && query.cursorId() != null) {
            sql.append(" AND (received_at < :cursorReceivedAt");
            sql.append(" OR (received_at = :cursorReceivedAt AND id < :cursorId))");
            params.addValue("cursorReceivedAt", query.cursorReceivedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            params.addValue("cursorId", query.cursorId(), Types.BIGINT);
        }

        sql.append(" ORDER BY received_at DESC, id DESC");
        sql.append(" LIMIT :limit");
        params.addValue("limit", query.limit(), Types.INTEGER);

        return jdbcTemplate.query(sql.toString(), params, rowMapper(query.includePayload()));
    }

    private RowMapper<WebhookFeedRow> rowMapper(boolean includePayload) {
        return (rs, rowNum) -> new WebhookFeedRow(
                rs.getLong("id"),
                rs.getString("event_id"),
                WebhookSource.valueOf(rs.getString("source")),
                rs.getString("event_type"),
                EventSupportState.valueOf(rs.getString("catalog_state")),
                NormalizedDomain.valueOf(rs.getString("normalized_domain")),
                NormalizedAction.valueOf(rs.getString("normalized_action")),
                WebhookEventStatus.valueOf(rs.getString("status")),
                getOffsetDateTime(rs),
                includePayload ? readPayload(rs) : null);
    }

    private OffsetDateTime getOffsetDateTime(ResultSet rs) throws SQLException {
        OffsetDateTime value = rs.getObject("received_at", OffsetDateTime.class);
        if (value != null) {
            return value;
        }
        java.sql.Timestamp timestamp = rs.getTimestamp("received_at");
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private JsonNode readPayload(ResultSet rs) throws SQLException {
        Object rawPayload = rs.getObject("payload");
        if (rawPayload == null) {
            return null;
        }
        try {
            if (rawPayload instanceof String payloadText) {
                return objectMapper.readTree(payloadText);
            }
            if (rawPayload instanceof byte[] payloadBytes) {
                return objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            }
            if ("org.postgresql.util.PGobject".equals(rawPayload.getClass().getName())) {
                String value = (String) rawPayload.getClass().getMethod("getValue").invoke(rawPayload);
                return value == null ? null : objectMapper.readTree(value);
            }
            return objectMapper.valueToTree(rawPayload);
        } catch (Exception ex) {
            throw new SQLException("Failed to parse payload JSON", ex);
        }
    }
}
