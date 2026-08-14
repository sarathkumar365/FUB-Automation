package com.flux.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Report 2 — per agent, of the leads that arrived in the window, how many they actually
 * spoke to.
 *
 * <p>Attribution, not coverage: a conversation is credited to whoever held the lead at the
 * moment of the call, so a reassignment cannot move someone else's neglect onto an agent.
 * Leads nobody acted on sit with their current holder, who is the person still holding them.
 *
 * <p>Counts here will not always match Report 1's, and that is deliberate — Report 1 groups
 * every lead under its current owner to measure coverage by channel.
 */
public record AccountabilityReportDto(
        ReportWindowDto window,
        ContactCountsDto totals,
        List<AgentRow> agents) {

    public record AgentRow(Long agentId, String agentName, ContactCountsDto counts) {}

    /**
     * The worklist behind an agent's red number: leads they currently hold where nothing at
     * all has happened, so the list length matches the count that was clicked.
     *
     * @param truncated true when more leads exist than {@code limit} — said out loud rather
     *                  than presenting a cut-off list as the whole of it
     */
    public record UnreachedLeads(
            ReportWindowDto window,
            Long agentId,
            String agentName,
            int limit,
            boolean truncated,
            List<Lead> leads) {

        public record Lead(
                String sourcePersonId,
                String name,
                String phone,
                String email,
                String source,
                OffsetDateTime arrivedAt) {}
    }
}
