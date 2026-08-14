package com.flux.service.reporting;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Locale;

/**
 * A reporting window, resolved as whole calendar days in the brokerage's timezone.
 *
 * <p>Calendar days rather than rolling hours: "yesterday" must give the same answer at
 * 9am and at 5pm, or two people reading the same board disagree about a settled fact.
 * A rolling 24-hour window slides, so it answers a different question every hour and
 * can never be quoted or compared week-on-week.
 *
 * <p>The timezone is the business one (`automation.business-hours.timezone`), not UTC —
 * a UTC "day" for a Toronto brokerage starts at 8pm the previous evening.
 *
 * <p>MONTH and YEAR need no new code here beyond another constant; the reports and the
 * API already take whatever bounds this produces.
 */
public enum ReportWindow {

    /** The last complete day. The default: finished, fixed, and the one operators ask for. */
    YESTERDAY,

    /** Today so far — the only window still moving. */
    TODAY,

    /** Monday through today, inclusive. */
    THIS_WEEK;

    public static ReportWindow fromKey(String key) {
        if (key == null || key.isBlank()) {
            return YESTERDAY;
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "yesterday" -> YESTERDAY;
            case "today" -> TODAY;
            case "this-week" -> THIS_WEEK;
            default -> throw new UnknownReportWindowException(key);
        };
    }

    public String key() {
        return switch (this) {
            case YESTERDAY -> "yesterday";
            case TODAY -> "today";
            case THIS_WEEK -> "this-week";
        };
    }

    /** Half-open {@code [from, to)} so a lead is never counted in two adjacent windows. */
    public Bounds resolve(Clock clock, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate startDay = switch (this) {
            case YESTERDAY -> today.minusDays(1);
            case TODAY -> today;
            case THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
        LocalDate endDayExclusive = this == YESTERDAY ? today : today.plusDays(1);

        return new Bounds(
                startDay.atStartOfDay(zone).toOffsetDateTime(),
                endDayExclusive.atStartOfDay(zone).toOffsetDateTime());
    }

    /** True while the window is still accumulating — its numbers can still change today. */
    public boolean isOpen() {
        return this != YESTERDAY;
    }

    public record Bounds(OffsetDateTime from, OffsetDateTime to) {}
}
