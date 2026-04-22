package com.fuba.automation_engine.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.LeadStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-model port for the leads feed. Implementation hand-writes JDBC
 * keyset pagination because we need a precise {@code (updated_at, id)}
 * compound ordering that JPA's Specification API expresses awkwardly.
 */
public interface LeadFeedReadRepository {

    List<LeadFeedRow> fetch(LeadFeedReadQuery query);

    /**
     * Feed query inputs.
     *
     * <p>{@code sourceSystem} / {@code status} / {@code from} / {@code to} are
     * optional exact-match filters. {@code sourceLeadIdPrefix} is a
     * starts-with match on the external lead id (v1 search scheme).
     *
     * <p>{@code cursorUpdatedAt} + {@code cursorId} must be provided together
     * or both null. The repository throws {@link IllegalArgumentException}
     * if only one is set.
     *
     * <p>{@code limit} — callers typically pass {@code userLimit + 1} so the
     * service layer can detect "has next page" without a separate count query.
     */
    record LeadFeedReadQuery(
            String sourceSystem,
            LeadStatus status,
            String sourceLeadIdPrefix,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorUpdatedAt,
            Long cursorId,
            int limit) {
    }

    record LeadFeedRow(
            Long id,
            String sourceSystem,
            String sourceLeadId,
            LeadStatus status,
            JsonNode leadDetails,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastSyncedAt) {
    }
}
