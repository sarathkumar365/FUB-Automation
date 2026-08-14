package com.flux.service.reporting.accountability;

import com.flux.config.BusinessHoursProperties;
import com.flux.config.CallOutcomeRulesProperties;
import com.flux.controller.dto.AccountabilityReportDto;
import com.flux.controller.dto.AccountabilityReportDto.AgentRow;
import com.flux.controller.dto.AccountabilityReportDto.UnreachedLeads;
import com.flux.controller.dto.ContactCountsDto;
import com.flux.controller.dto.ReportWindowDto;
import com.flux.persistence.repository.AccountabilityReadRepository;
import com.flux.persistence.repository.AccountabilityReadRepository.AgentStateRow;
import com.flux.persistence.repository.AccountabilityReadRepository.UnreachedLeadRow;
import com.flux.persistence.repository.SourceContactReadRepository;
import com.flux.service.reporting.LeadSourceResolver;
import com.flux.service.reporting.ReportWindow;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountabilityReportService {

    private static final String STATE_SPOKE = "SPOKE";
    private static final String STATE_ATTEMPTED = "ATTEMPTED";
    private static final String STATE_NOTHING = "NOTHING";

    /** Caps the drill-down. A worklist is for working through, not for scrolling forever. */
    static final int MAX_UNREACHED = 200;

    private final AccountabilityReadRepository readRepository;
    private final SourceContactReadRepository sourceContactReadRepository;
    private final LeadSourceResolver sourceResolver;
    private final CallOutcomeRulesProperties callOutcomeRules;
    private final BusinessHoursProperties businessHours;
    private final Clock clock;

    public AccountabilityReportService(
            AccountabilityReadRepository readRepository,
            SourceContactReadRepository sourceContactReadRepository,
            LeadSourceResolver sourceResolver,
            CallOutcomeRulesProperties callOutcomeRules,
            BusinessHoursProperties businessHours,
            Clock clock) {
        this.readRepository = readRepository;
        this.sourceContactReadRepository = sourceContactReadRepository;
        this.sourceResolver = sourceResolver;
        this.callOutcomeRules = callOutcomeRules;
        this.businessHours = businessHours;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public AccountabilityReportDto report(ReportWindow window) {
        ZoneId zone = ZoneId.of(businessHours.getTimezone());
        ReportWindow.Bounds bounds = window.resolve(clock, zone);

        List<AgentStateRow> rows = readRepository.countByAgentAndState(
                bounds.from(), bounds.to(), threshold());

        return new AccountabilityReportDto(
                window(window, bounds, zone),
                totals(rows),
                agents(rows));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRED)
    public UnreachedLeads unreached(ReportWindow window, long agentId) {
        ZoneId zone = ZoneId.of(businessHours.getTimezone());
        ReportWindow.Bounds bounds = window.resolve(clock, zone);

        // Ask for one more than the cap so we can honestly say the list was cut short
        // rather than presenting a truncated worklist as if it were complete.
        List<UnreachedLeadRow> rows = readRepository.unreachedLeads(
                bounds.from(), bounds.to(), threshold(), agentId, MAX_UNREACHED + 1);
        boolean truncated = rows.size() > MAX_UNREACHED;
        List<UnreachedLeadRow> page = truncated ? rows.subList(0, MAX_UNREACHED) : rows;

        List<UnreachedLeads.Lead> leads = page.stream()
                .map(r -> new UnreachedLeads.Lead(
                        r.sourcePersonId(),
                        r.name(),
                        r.phone(),
                        r.email(),
                        sourceResolver.resolve(r.rawSource()),
                        r.arrivedAt()))
                .toList();

        return new UnreachedLeads(
                window(window, bounds, zone), agentId, readRepository.agentName(agentId),
                MAX_UNREACHED, truncated, leads);
    }

    private ReportWindowDto window(ReportWindow window, ReportWindow.Bounds bounds, ZoneId zone) {
        long eventsReceived = sourceContactReadRepository.countEventsReceived(bounds.from(), bounds.to());
        return new ReportWindowDto(window.key(), bounds.from(), bounds.to(), zone.getId(), window.isOpen(), eventsReceived);
    }

    private int threshold() {
        return callOutcomeRules.getShortCallThresholdSeconds();
    }

    private ContactCountsDto totals(List<AgentStateRow> rows) {
        Accumulator all = new Accumulator();
        rows.forEach(all::add);
        return all.toCounts();
    }

    private List<AgentRow> agents(List<AgentStateRow> rows) {
        Map<Long, Accumulator> byAgent = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (AgentStateRow row : rows) {
            byAgent.computeIfAbsent(row.agentId(), k -> new Accumulator()).add(row);
            if (row.agentName() != null) {
                names.putIfAbsent(row.agentId(), row.agentName());
            }
        }

        List<AgentRow> out = new ArrayList<>(byAgent.size());
        byAgent.forEach((agentId, acc) -> out.add(new AgentRow(agentId, names.get(agentId), acc.toCounts())));
        // Most leads first; the agent sitting on the biggest pile is the one to look at.
        out.sort(Comparator.comparingLong((AgentRow a) -> a.counts().leads()).reversed());
        return out;
    }

    private static final class Accumulator {
        private long spoke;
        private long attempted;
        private long nothing;

        void add(AgentStateRow row) {
            switch (row.state()) {
                case STATE_SPOKE -> spoke += row.leads();
                case STATE_ATTEMPTED -> attempted += row.leads();
                case STATE_NOTHING -> nothing += row.leads();
                // Never default into "nothing" — an unknown state would be reported as
                // neglected leads, which is the number people are confronted with.
                default -> throw new IllegalStateException("Unknown contact state: " + row.state());
            }
        }

        ContactCountsDto toCounts() {
            return new ContactCountsDto(spoke + attempted + nothing, spoke, attempted, nothing);
        }
    }
}
