package com.flux.service.reporting;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportWindowTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    /** 13 Aug 2026, 01:30 UTC — which is still 12 Aug, 21:30 in Toronto. */
    private static final Clock LATE_EVENING_TORONTO =
            Clock.fixed(Instant.parse("2026-08-13T01:30:00Z"), ZoneOffset.UTC);

    /** 13 Aug 2026, 18:00 UTC — 14:00 in Toronto, mid-afternoon on a Thursday. */
    private static final Clock THURSDAY_AFTERNOON =
            Clock.fixed(Instant.parse("2026-08-13T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void yesterdayIsAWholeCalendarDayInTheBusinessTimezone() {
        var bounds = ReportWindow.YESTERDAY.resolve(THURSDAY_AFTERNOON, TORONTO);

        assertThat(bounds.from().toString()).startsWith("2026-08-12T00:00");
        assertThat(bounds.to().toString()).startsWith("2026-08-13T00:00");
    }

    @Test
    void theDayBoundaryFollowsTorontoNotUtc() {
        // At 01:30 UTC it is still the 12th in Toronto, so "yesterday" is the 11th.
        // Bucketing in UTC would wrongly call it the 12th and shift a whole evening
        // of leads into the wrong day.
        var bounds = ReportWindow.YESTERDAY.resolve(LATE_EVENING_TORONTO, TORONTO);

        assertThat(bounds.from().toString()).startsWith("2026-08-11T00:00");
        assertThat(bounds.to().toString()).startsWith("2026-08-12T00:00");
    }

    @Test
    void yesterdayDoesNotMoveAsTheDayGoesOn() {
        Clock morning = Clock.fixed(Instant.parse("2026-08-13T13:00:00Z"), ZoneOffset.UTC);
        Clock evening = Clock.fixed(Instant.parse("2026-08-13T22:00:00Z"), ZoneOffset.UTC);

        assertThat(ReportWindow.YESTERDAY.resolve(morning, TORONTO))
                .isEqualTo(ReportWindow.YESTERDAY.resolve(evening, TORONTO));
    }

    @Test
    void todayRunsToTheEndOfTheCurrentDayAndIsStillOpen() {
        var bounds = ReportWindow.TODAY.resolve(THURSDAY_AFTERNOON, TORONTO);

        assertThat(bounds.from().toString()).startsWith("2026-08-13T00:00");
        assertThat(bounds.to().toString()).startsWith("2026-08-14T00:00");
        assertThat(ReportWindow.TODAY.isOpen()).isTrue();
        assertThat(ReportWindow.YESTERDAY.isOpen()).isFalse();
    }

    @Test
    void thisWeekStartsOnMondayAndIncludesToday() {
        // 13 Aug 2026 is a Thursday; the week starts Monday the 10th.
        var bounds = ReportWindow.THIS_WEEK.resolve(THURSDAY_AFTERNOON, TORONTO);

        assertThat(bounds.from().toString()).startsWith("2026-08-10T00:00");
        assertThat(bounds.to().toString()).startsWith("2026-08-14T00:00");
    }

    @Test
    void windowsAreHalfOpenSoNoLeadFallsInTwoOfThem() {
        var yesterday = ReportWindow.YESTERDAY.resolve(THURSDAY_AFTERNOON, TORONTO);
        var today = ReportWindow.TODAY.resolve(THURSDAY_AFTERNOON, TORONTO);

        assertThat(yesterday.to()).isEqualTo(today.from());
    }

    @Test
    void keysRoundTripAndDefaultToYesterday() {
        assertThat(ReportWindow.fromKey("yesterday")).isEqualTo(ReportWindow.YESTERDAY);
        assertThat(ReportWindow.fromKey("TODAY")).isEqualTo(ReportWindow.TODAY);
        assertThat(ReportWindow.fromKey("this-week")).isEqualTo(ReportWindow.THIS_WEEK);
        assertThat(ReportWindow.fromKey(null)).isEqualTo(ReportWindow.YESTERDAY);
        assertThat(ReportWindow.fromKey("  ")).isEqualTo(ReportWindow.YESTERDAY);

        for (ReportWindow w : ReportWindow.values()) {
            assertThat(ReportWindow.fromKey(w.key())).isEqualTo(w);
        }
    }

    @Test
    void anUnknownWindowIsRejectedRatherThanSilentlyDefaulted() {
        assertThatThrownBy(() -> ReportWindow.fromKey("last-quarter"))
                .isInstanceOf(UnknownReportWindowException.class)
                .hasMessageContaining("last-quarter");
    }
}
