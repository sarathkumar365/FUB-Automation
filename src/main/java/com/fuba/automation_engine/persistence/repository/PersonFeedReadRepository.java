package com.fuba.automation_engine.persistence.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuba.automation_engine.persistence.entity.PersonStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-model port for the persons feed. Implementation hand-writes JDBC
 * keyset pagination because we need a precise {@code (updated_at, id)}
 * compound ordering that JPA's Specification API expresses awkwardly.
 */
public interface PersonFeedReadRepository {

    List<PersonFeedRow> fetch(PersonFeedReadQuery query);

    /**
     * Feed query inputs.
     *
     * <p>{@code sourceSystem} / {@code status} / {@code from} / {@code to} are
     * optional exact-match filters. {@code sourcePersonIdPrefix} is a
     * starts-with match on the external person id (v1 search scheme).
     *
     * <p>{@code cursorUpdatedAt} + {@code cursorId} must be provided together
     * or both null. The repository throws {@link IllegalArgumentException}
     * if only one is set.
     *
     * <p>{@code limit} — callers typically pass {@code userLimit + 1} so the
     * service layer can detect "has next page" without a separate count query.
     */
    record PersonFeedReadQuery(
            String sourceSystem,
            PersonStatus status,
            String sourcePersonIdPrefix,
            OffsetDateTime from,
            OffsetDateTime to,
            OffsetDateTime cursorUpdatedAt,
            Long cursorId,
            int limit) {
    }

    record PersonFeedRow(
            Long id,
            String sourceSystem,
            String sourcePersonId,
            PersonStatus status,
            JsonNode personDetails,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastSyncedAt) {
    }
}
