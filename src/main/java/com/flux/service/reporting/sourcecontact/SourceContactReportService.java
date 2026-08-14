package com.flux.service.reporting.sourcecontact;

import com.flux.config.BusinessHoursProperties;
import com.flux.config.CallOutcomeRulesProperties;
import com.flux.controller.dto.SourceContactReportDto;
import com.flux.controller.dto.SourceContactReportDto.AgentRow;
import com.flux.controller.dto.ContactCountsDto;
import com.flux.controller.dto.SourceContactReportDto.SourceRow;
import com.flux.controller.dto.ReportWindowDto;
import com.flux.persistence.repository.SourceContactReadRepository;
import com.flux.persistence.repository.SourceContactReadRepository.SourceContactRow;
import com.flux.service.reporting.LeadSourceResolver;
import com.flux.service.reporting.ReportWindow;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceContactReportService {

    private static final String STATE_SPOKE = "SPOKE";
    private static final String STATE_ATTEMPTED = "ATTEMPTED";
    private static final String STATE_NOTHING = "NOTHING";

    private final SourceContactReadRepository readRepository;
    private final LeadSourceResolver sourceResolver;
    private final CallOutcomeRulesProperties callOutcomeRules;
    private final BusinessHoursProperties businessHours;
    private final Clock clock;

    public SourceContactReportService(
            SourceContactReadRepository readRepository,
            LeadSourceResolver sourceResolver,
            CallOutcomeRulesProperties callOutcomeRules,
            BusinessHoursProperties businessHours,
            Clock clock) {
        this.readRepository = readRepository;
        this.sourceResolver = sourceResolver;
        this.callOutcomeRules = callOutcomeRules;
        this.businessHours = businessHours;
        this.clock = clock;
    }

    // REPEATABLE_READ so the totals and the per-source breakdown are read from one DB
    // snapshot — otherwise a call landing mid-report can make them disagree. REQUIRED,
    // never REQUIRES_NEW (known-issue #31).
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public SourceContactReportDto report(ReportWindow window) {
        ZoneId zone = ZoneId.of(businessHours.getTimezone());
        ReportWindow.Bounds bounds = window.resolve(clock, zone);

        // The threshold is config, never a literal in SQL, so the report and the
        // workflow engine can never disagree about what "a conversation" means.
        List<SourceContactRow> rows = readRepository.countBySourceAgentAndState(
                bounds.from(), bounds.to(), callOutcomeRules.getShortCallThresholdSeconds());

        long eventsReceived = readRepository.countEventsReceived(bounds.from(), bounds.to());

        return new SourceContactReportDto(
                new ReportWindowDto(window.key(), bounds.from(), bounds.to(), zone.getId(), window.isOpen(), eventsReceived),
                totals(rows),
                sources(rows));
    }

    private ContactCountsDto totals(List<SourceContactRow> rows) {
        Accumulator all = new Accumulator();
        rows.forEach(all::add);
        return all.toCounts();
    }

    private List<SourceRow> sources(List<SourceContactRow> rows) {
        // Source names are folded here rather than in SQL: they are one company's ad
        // channels, not schema, and a new channel must not need a migration.
        Map<String, Map<Long, Accumulator>> bySource = new LinkedHashMap<>();
        Map<String, Accumulator> sourceTotals = new LinkedHashMap<>();
        Map<Long, Map<String, Long>> nameVotes = new HashMap<>();

        for (SourceContactRow row : rows) {
            String source = sourceResolver.resolve(row.rawSource());
            sourceTotals.computeIfAbsent(source, k -> new Accumulator()).add(row);
            bySource.computeIfAbsent(source, k -> new LinkedHashMap<>())
                    .computeIfAbsent(row.agentId(), k -> new Accumulator())
                    .add(row);
            if (row.agentName() != null) {
                // One id can carry several spellings ("Arjun Ahluwalia" / "Arjun Singh
                // Ahluwalia"), and one id is shared by two labels. Group by id and let
                // the most-used spelling win, so an agent never appears twice.
                nameVotes.computeIfAbsent(row.agentId(), k -> new HashMap<>())
                        .merge(row.agentName(), row.leads(), Long::sum);
            }
        }

        List<SourceRow> out = new ArrayList<>(bySource.size());
        for (Map.Entry<String, Map<Long, Accumulator>> entry : bySource.entrySet()) {
            List<AgentRow> agents = entry.getValue().entrySet().stream()
                    .map(agent -> new AgentRow(
                            agent.getKey(),
                            canonicalName(nameVotes, agent.getKey()),
                            agent.getValue().toCounts()))
                    .sorted(Comparator.comparingLong((AgentRow a) -> a.counts().leads()).reversed())
                    .toList();
            out.add(new SourceRow(entry.getKey(), sourceTotals.get(entry.getKey()).toCounts(), agents));
        }
        out.sort(Comparator.comparingLong((SourceRow s) -> s.counts().leads()).reversed());
        return out;
    }

    private String canonicalName(Map<Long, Map<String, Long>> votes, Long agentId) {
        Map<String, Long> forAgent = votes.get(agentId);
        if (forAgent == null || forAgent.isEmpty()) {
            return null;
        }
        // Alphabetical as the tie-break: without it a 3-3 split between two spellings
        // resolves by hash order, so the same agent's name could change between runs.
        return forAgent.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static final class Accumulator {
        private long spoke;
        private long attempted;
        private long nothing;

        void add(SourceContactRow row) {
            switch (row.state()) {
                case STATE_SPOKE -> spoke += row.leads();
                case STATE_ATTEMPTED -> attempted += row.leads();
                case STATE_NOTHING -> nothing += row.leads();
                // Never default into "nothing": a state the query starts returning but
                // this switch doesn't know would silently be reported as neglected leads,
                // which is the number managers confront people with. Fail loudly instead.
                default -> throw new IllegalStateException("Unknown contact state: " + row.state());
            }
        }

        ContactCountsDto toCounts() {
            return new ContactCountsDto(spoke + attempted + nothing, spoke, attempted, nothing);
        }
    }
}
