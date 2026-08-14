package com.flux.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read repo for Report 1 (source → agent → contact outcome). Reporting-local by
 * design — deliberately not the RD-012 {@code ReportingQuery} port, which is
 * extracted in Phase 3 once two reports exist to extract it from.
 */
public interface SourceContactReadRepository {

    /**
     * One row per (raw source, holder, contact state) for leads that <em>arrived</em>
     * in the window. Raw source is returned verbatim — folding into display buckets
     * is the service's job, so the mapping stays out of SQL.
     *
     * @param conversationThresholdSeconds a call must exceed this to count as a conversation
     */
    List<SourceContactRow> countBySourceAgentAndState(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds);

    /**
     * Webhooks received in the window. Lets the caller tell "no leads arrived" apart
     * from "we received nothing", which look identical in the counts above and mean
     * opposite things.
     */
    long countEventsReceived(OffsetDateTime from, OffsetDateTime to);

    /** {@code state} is one of {@code SPOKE}, {@code ATTEMPTED}, {@code NOTHING}. */
    record SourceContactRow(String rawSource, Long agentId, String agentName, String state, long leads) {}
}
