package com.flux.service.reporting.sourcecontact;

import com.flux.config.BusinessHoursProperties;
import com.flux.config.CallOutcomeRulesProperties;
import com.flux.controller.dto.SourceContactReportDto;
import com.flux.controller.dto.SourceContactReportDto.SourceRow;
import com.flux.persistence.repository.SourceContactReadRepository;
import com.flux.persistence.repository.SourceContactReadRepository.SourceContactRow;
import com.flux.service.reporting.LeadSourceResolver;
import com.flux.service.reporting.ReportWindow;
import com.flux.service.reporting.StaticLeadSourceResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceContactReportServiceTest {

    private static final Clock THURSDAY = Clock.fixed(Instant.parse("2026-08-13T18:00:00Z"), ZoneOffset.UTC);

    private StubRepository repository;
    private SourceContactReportService service;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        LeadSourceResolver resolver = new StaticLeadSourceResolver();
        CallOutcomeRulesProperties rules = new CallOutcomeRulesProperties();
        BusinessHoursProperties hours = new BusinessHoursProperties();
        service = new SourceContactReportService(repository, resolver, rules, hours, THURSDAY);
    }

    @Test
    void spellingVariantsOfOneChannelBecomeOneRow() {
        repository.rows = List.of(
                row("Instagram", 14L, "Karanjot", "SPOKE", 3),
                row("Insta", 14L, "Karanjot", "SPOKE", 1),
                row("Intragram", 14L, "Karanjot", "NOTHING", 2));

        SourceContactReportDto report = service.report(ReportWindow.YESTERDAY);

        assertThat(report.sources()).hasSize(1);
        SourceRow instagram = report.sources().get(0);
        assertThat(instagram.source()).isEqualTo("Instagram");
        assertThat(instagram.counts().leads()).isEqualTo(6);
        assertThat(instagram.counts().spoke()).isEqualTo(4);
        assertThat(instagram.counts().nothing()).isEqualTo(2);
    }

    @Test
    void theThreeStatesAlwaysSumToTheLeadCount() {
        repository.rows = List.of(
                row("facebook", 1L, "Mandeep", "SPOKE", 5),
                row("facebook", 1L, "Mandeep", "ATTEMPTED", 9),
                row("facebook", 1L, "Mandeep", "NOTHING", 11));

        var totals = service.report(ReportWindow.YESTERDAY).totals();

        assertThat(totals.leads()).isEqualTo(25);
        assertThat(totals.spoke() + totals.attempted() + totals.nothing()).isEqualTo(totals.leads());
    }

    @Test
    void oneAgentWithTwoSpellingsIsNotShownTwice() {
        // uid 31 carries both "Arjun Ahluwalia" and "Arjun Singh Ahluwalia" in FUB.
        repository.rows = List.of(
                row("facebook", 31L, "Arjun Ahluwalia", "SPOKE", 5),
                row("facebook", 31L, "Arjun Singh Ahluwalia", "NOTHING", 2));

        var agents = service.report(ReportWindow.YESTERDAY).sources().get(0).agents();

        assertThat(agents).hasSize(1);
        assertThat(agents.get(0).agentId()).isEqualTo(31L);
        assertThat(agents.get(0).agentName()).isEqualTo("Arjun Ahluwalia"); // the more-used spelling
        assertThat(agents.get(0).counts().leads()).isEqualTo(7);
    }

    @Test
    void anUnrecognisedStateFailsRatherThanCountingAsNeglected() {
        // Silently bucketing an unknown state as "nothing" would report leads as never
        // contacted when they were — the one number people are confronted with.
        repository.rows = List.of(row("facebook", 1L, "Mandeep", "SOMETHING_NEW", 4));

        assertThatThrownBy(() -> service.report(ReportWindow.YESTERDAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOMETHING_NEW");
    }

    @Test
    void tiedNameSpellingsResolveDeterministically() {
        repository.rows = List.of(
                row("facebook", 31L, "Arjun Ahluwalia", "SPOKE", 3),
                row("facebook", 31L, "Arjun Singh Ahluwalia", "NOTHING", 3));

        var first = service.report(ReportWindow.YESTERDAY).sources().get(0).agents().get(0).agentName();

        repository.rows = List.of(
                row("facebook", 31L, "Arjun Singh Ahluwalia", "NOTHING", 3),
                row("facebook", 31L, "Arjun Ahluwalia", "SPOKE", 3));

        var second = service.report(ReportWindow.YESTERDAY).sources().get(0).agents().get(0).agentName();

        assertThat(first).isEqualTo(second).isEqualTo("Arjun Ahluwalia");
    }

    @Test
    void sourcesAndAgentsAreOrderedByVolume() {
        repository.rows = List.of(
                row("Instagram", 14L, "Karanjot", "SPOKE", 2),
                row("facebook", 1L, "Mandeep", "NOTHING", 30),
                row("facebook", 14L, "Karanjot", "SPOKE", 4));

        var report = service.report(ReportWindow.YESTERDAY);

        assertThat(report.sources().get(0).source()).isEqualTo("Facebook");
        assertThat(report.sources().get(0).agents().get(0).agentName()).isEqualTo("Mandeep");
    }

    @Test
    void anUnknownChannelStaysVisibleInsteadOfVanishing() {
        repository.rows = List.of(row("LinkedIn Ads", 1L, "Mandeep", "SPOKE", 3));

        var report = service.report(ReportWindow.YESTERDAY);

        assertThat(report.sources()).singleElement()
                .extracting(SourceRow::source).isEqualTo("Other");
        assertThat(report.totals().leads()).isEqualTo(3);
    }

    @Test
    void theWindowIsReportedBackWithItsTimezoneAndOpenState() {
        repository.rows = List.of();
        repository.events = 84;

        var window = service.report(ReportWindow.YESTERDAY).window();

        assertThat(window.key()).isEqualTo("yesterday");
        assertThat(window.timezone()).isEqualTo("America/Toronto");
        assertThat(window.open()).isFalse();
        assertThat(window.from().toString()).startsWith("2026-08-12T00:00");
        assertThat(window.eventsReceived()).isEqualTo(84);
    }

    @Test
    void anEmptyWindowStillReportsWhetherAnythingWasReceived() {
        // Zero leads with events received means a quiet day; zero leads with zero
        // events means the feed was down. The UI has to tell those apart.
        repository.rows = List.of();
        repository.events = 0;

        var report = service.report(ReportWindow.TODAY);

        assertThat(report.totals().leads()).isZero();
        assertThat(report.sources()).isEmpty();
        assertThat(report.window().eventsReceived()).isZero();
        assertThat(report.window().open()).isTrue();
    }

    @Test
    void theConfiguredConversationThresholdIsPassedToTheQuery() {
        repository.rows = List.of();

        service.report(ReportWindow.YESTERDAY);

        // Default from CallOutcomeRulesProperties — never a literal in the SQL, so the
        // report and the workflow engine cannot disagree about what a conversation is.
        assertThat(repository.thresholdSeen).isEqualTo(30);
    }

    private static SourceContactRow row(String source, Long agentId, String name, String state, long leads) {
        return new SourceContactRow(source, agentId, name, state, leads);
    }

    private static final class StubRepository implements SourceContactReadRepository {
        private List<SourceContactRow> rows = List.of();
        private long events;
        private int thresholdSeen = -1;

        @Override
        public List<SourceContactRow> countBySourceAgentAndState(
                OffsetDateTime from, OffsetDateTime to, int conversationThresholdSeconds) {
            this.thresholdSeen = conversationThresholdSeconds;
            return rows;
        }

        @Override
        public long countEventsReceived(OffsetDateTime from, OffsetDateTime to) {
            return events;
        }
    }
}
