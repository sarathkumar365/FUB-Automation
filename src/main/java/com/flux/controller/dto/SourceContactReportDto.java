package com.flux.controller.dto;

import java.util.List;

/**
 * Report 1 — where leads came from, who holds them, and whether anyone reached them.
 *
 * <p>Coverage, not a scorecard: leads are counted once against their <em>current</em>
 * holder, so the totals reconcile to arrivals. Per-agent credit is Report 2's job,
 * which attributes to the holder at the moment of each call and therefore counts
 * differently on purpose.
 */
public record SourceContactReportDto(
        ReportWindowDto window,
        ContactCountsDto totals,
        List<SourceRow> sources) {

    public record SourceRow(String source, ContactCountsDto counts, List<AgentRow> agents) {}

    public record AgentRow(Long agentId, String agentName, ContactCountsDto counts) {}
}
