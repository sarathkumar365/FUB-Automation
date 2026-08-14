package com.flux.service.reporting.accountability;

import com.flux.config.BusinessHoursProperties;
import com.flux.config.CallOutcomeRulesProperties;
import com.flux.controller.dto.AccountabilityReportDto;
import com.flux.controller.dto.AccountabilityReportDto.AgentRow;
import com.flux.persistence.repository.AccountabilityReadRepository;
import com.flux.persistence.repository.SourceContactReadRepository;
import com.flux.service.reporting.LeadSourceResolver;
import com.flux.service.reporting.ReportWindow;
import com.flux.service.reporting.StaticLeadSourceResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountabilityReportServiceTest {

    private static final Clock THURSDAY = Clock.fixed(Instant.parse("2026-08-13T18:00:00Z"), ZoneOffset.UTC);

    private StubRepository repository;
    private AccountabilityReportService service;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        LeadSourceResolver resolver = new StaticLeadSourceResolver();
        service = new AccountabilityReportService(
                repository, repository, resolver,
                new CallOutcomeRulesProperties(), new BusinessHoursProperties(), THURSDAY);
    }

    @Test
    void eachAgentsThreeStatesSumToTheirLeadCount() {
        repository.stateRows = List.of(
                agentRow(1L, "Mandeep", "SPOKE", 2),
                agentRow(1L, "Mandeep", "ATTEMPTED", 5),
                agentRow(1L, "Mandeep", "NOTHING", 9));

        AccountabilityReportDto report = service.report(ReportWindow.YESTERDAY);

        AgentRow mandeep = report.agents().get(0);
        assertThat(mandeep.counts().leads()).isEqualTo(16);
        assertThat(mandeep.counts().spoke() + mandeep.counts().attempted() + mandeep.counts().nothing())
                .isEqualTo(16);
        assertThat(report.totals().leads()).isEqualTo(16);
    }

    @Test
    void agentsAreOrderedByLeadVolumeSoTheBiggestPileIsFirst() {
        repository.stateRows = List.of(
                agentRow(14L, "Karanjot", "SPOKE", 4),
                agentRow(1L, "Mandeep", "NOTHING", 30));

        var agents = service.report(ReportWindow.YESTERDAY).agents();

        assertThat(agents).extracting(AgentRow::agentName).containsExactly("Mandeep", "Karanjot");
    }

    @Test
    void anUnrecognisedStateFailsRatherThanCountingAsNeglected() {
        repository.stateRows = List.of(agentRow(1L, "Mandeep", "SOMETHING_NEW", 3));

        assertThatThrownBy(() -> service.report(ReportWindow.YESTERDAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOMETHING_NEW");
    }

    @Test
    void theUnreachedWorklistResolvesLeadSourceNames() {
        repository.unreachedRows = List.of(unreachedRow("500", "Intragram"));

        var worklist = service.unreached(ReportWindow.YESTERDAY, 1L);

        assertThat(worklist.leads()).singleElement()
                .extracting(l -> l.source()).isEqualTo("Instagram");
        assertThat(worklist.agentId()).isEqualTo(1L);
        assertThat(worklist.truncated()).isFalse();
    }

    @Test
    void anOversizedWorklistIsCappedAndSaysSo() {
        // Silently truncating would present a partial worklist as if it were the whole
        // list, so an agent could work it to the end and still have leads waiting.
        repository.unreachedRows = IntStream.range(0, AccountabilityReportService.MAX_UNREACHED + 25)
                .mapToObj(i -> unreachedRow("lead-" + i, "facebook"))
                .toList();

        var worklist = service.unreached(ReportWindow.YESTERDAY, 1L);

        assertThat(worklist.leads()).hasSize(AccountabilityReportService.MAX_UNREACHED);
        assertThat(worklist.truncated()).isTrue();
        assertThat(worklist.limit()).isEqualTo(AccountabilityReportService.MAX_UNREACHED);
    }

    @Test
    void aWorklistExactlyAtTheCapIsNotReportedAsTruncated() {
        repository.unreachedRows = IntStream.range(0, AccountabilityReportService.MAX_UNREACHED)
                .mapToObj(i -> unreachedRow("lead-" + i, "facebook"))
                .toList();

        var worklist = service.unreached(ReportWindow.YESTERDAY, 1L);

        assertThat(worklist.leads()).hasSize(AccountabilityReportService.MAX_UNREACHED);
        assertThat(worklist.truncated()).isFalse();
    }

    @Test
    void theWindowIsReportedBackWithItsTimezoneAndLiveness() {
        repository.stateRows = List.of();
        repository.events = 84;

        var window = service.report(ReportWindow.TODAY).window();

        assertThat(window.key()).isEqualTo("today");
        assertThat(window.timezone()).isEqualTo("America/Toronto");
        assertThat(window.open()).isTrue();
        assertThat(window.eventsReceived()).isEqualTo(84);
    }

    private static AccountabilityReadRepository.AgentStateRow agentRow(
            Long id, String name, String state, long leads) {
        return new AccountabilityReadRepository.AgentStateRow(id, name, state, leads);
    }

    private static AccountabilityReadRepository.UnreachedLeadRow unreachedRow(String id, String source) {
        return new AccountabilityReadRepository.UnreachedLeadRow(
                id, "Lead " + id, "6470000000", id + "@example.com", source,
                OffsetDateTime.parse("2026-08-12T14:00:00Z"));
    }

    /** Stands in for both read repos so the service is exercised without a database. */
    private static final class StubRepository
            implements AccountabilityReadRepository, SourceContactReadRepository {

        private List<AgentStateRow> stateRows = List.of();
        private List<UnreachedLeadRow> unreachedRows = List.of();
        private long events;

        @Override
        public List<AgentStateRow> countByAgentAndState(OffsetDateTime from, OffsetDateTime to, int threshold) {
            return stateRows;
        }

        @Override
        public List<UnreachedLeadRow> unreachedLeads(
                OffsetDateTime from, OffsetDateTime to, int threshold, long agentId, int limit) {
            return new ArrayList<>(unreachedRows.subList(0, Math.min(limit, unreachedRows.size())));
        }

        @Override
        public String agentName(long agentId) {
            return "Agent " + agentId;
        }

        @Override
        public List<SourceContactRow> countBySourceAgentAndState(
                OffsetDateTime from, OffsetDateTime to, int threshold) {
            return List.of();
        }

        @Override
        public long countEventsReceived(OffsetDateTime from, OffsetDateTime to) {
            return events;
        }
    }
}
