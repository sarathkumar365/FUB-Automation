package com.flux.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;

/** Read repo for Report 2 (agent accountability). Reporting-local, like its Report 1 sibling. */
public interface AccountabilityReadRepository {

    /**
     * One row per (agent, contact state) for leads that arrived in the window.
     *
     * <p>Each lead lands under exactly one agent, so the rows sum back to the arrival
     * count: whoever acted on it if anyone did, otherwise whoever holds it now and is
     * therefore the person still sitting on it.
     */
    List<AgentStateRow> countByAgentAndState(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds);

    /**
     * The leads behind an agent's red number: those they currently hold where nothing at all
     * has happened. Keyed on the current holder rather than on who last acted, so the list is
     * always actionable by the person reading it, and scoped to the same state the board shows
     * so its length matches the number that was clicked.
     */
    List<UnreachedLeadRow> unreachedLeads(
            OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds, long agentId, int limit);

    /** Canonical display name for a user id, or null if we have never seen one. */
    String agentName(long agentId);

    /** {@code state} is one of {@code SPOKE}, {@code ATTEMPTED}, {@code NOTHING}. */
    record AgentStateRow(Long agentId, String agentName, String state, long leads) {}

    record UnreachedLeadRow(
            String sourcePersonId,
            String name,
            String phone,
            String email,
            String rawSource,
            OffsetDateTime arrivedAt) {}
}
