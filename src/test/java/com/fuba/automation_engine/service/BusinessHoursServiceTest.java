package com.fuba.automation_engine.service;

import com.fuba.automation_engine.config.BusinessHoursProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessHoursServiceTest {

    private static BusinessHoursProperties defaults() {
        BusinessHoursProperties p = new BusinessHoursProperties();
        p.setTimezone("America/Toronto");
        p.setStartHour(9);
        p.setEndHour(18);
        p.setWeekdaysOnly(true);
        return p;
    }

    private static BusinessHoursService svc(BusinessHoursProperties p) {
        return new BusinessHoursService(p, Clock.systemUTC());
    }

    private static Instant torontoLocal(int year, int month, int day, int hour) {
        return ZonedDateTime.of(LocalDate.of(year, month, day).atTime(hour, 0),
                ZoneId.of("America/Toronto")).toInstant();
    }

    @Test
    void shouldReturnTrueDuringDaytimeOnWeekday() {
        BusinessHoursService service = svc(defaults());
        // Wednesday 2026-04-15 14:00 in Toronto.
        assertTrue(service.isDaytime(torontoLocal(2026, 4, 15, 14)));
    }

    @Test
    void shouldReturnFalseBeforeStartHour() {
        BusinessHoursService service = svc(defaults());
        // 08:30 (before 9).
        Instant t = ZonedDateTime.of(2026, 4, 15, 8, 30, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        assertFalse(service.isDaytime(t));
    }

    @Test
    void shouldReturnFalseAtExactlyEndHour() {
        // Half-open interval: endHour=18 means 18:00 is OFF-hours, 17:59 is daytime.
        BusinessHoursService service = svc(defaults());
        assertFalse(service.isDaytime(torontoLocal(2026, 4, 15, 18)));
        Instant minuteBeforeEnd = ZonedDateTime.of(2026, 4, 15, 17, 59, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        assertTrue(service.isDaytime(minuteBeforeEnd));
    }

    @Test
    void shouldReturnTrueAtExactlyStartHour() {
        // Inclusive lower bound: 09:00 is daytime.
        BusinessHoursService service = svc(defaults());
        assertTrue(service.isDaytime(torontoLocal(2026, 4, 15, 9)));
    }

    @Test
    void shouldReturnFalseForWeekendWhenWeekdaysOnly() {
        BusinessHoursService service = svc(defaults());
        // Saturday 2026-04-18 14:00 Toronto.
        assertFalse(service.isDaytime(torontoLocal(2026, 4, 18, 14)));
        // Sunday 2026-04-19 14:00 Toronto.
        assertFalse(service.isDaytime(torontoLocal(2026, 4, 19, 14)));
    }

    @Test
    void shouldEvaluateWeekendsLikeWeekdaysWhenWeekdaysOnlyDisabled() {
        BusinessHoursProperties p = defaults();
        p.setWeekdaysOnly(false);
        BusinessHoursService service = svc(p);
        // Saturday 14:00 Toronto -> daytime now.
        assertTrue(service.isDaytime(torontoLocal(2026, 4, 18, 14)));
        // Saturday 23:00 Toronto -> still off-hours by hour rule.
        assertFalse(service.isDaytime(torontoLocal(2026, 4, 18, 23)));
    }

    @Test
    void shouldRespectTimezoneDifferences() {
        BusinessHoursProperties p = defaults();
        p.setTimezone("America/Los_Angeles");
        BusinessHoursService laService = svc(p);

        // Wednesday 2026-04-15 14:00 Toronto = 11:00 LA -> daytime in both zones.
        Instant fourteenToronto = torontoLocal(2026, 4, 15, 14);
        assertTrue(laService.isDaytime(fourteenToronto));

        // Wednesday 2026-04-15 22:00 Toronto = 19:00 LA -> off-hours in LA.
        Instant tenPmToronto = torontoLocal(2026, 4, 15, 22);
        assertFalse(laService.isDaytime(tenPmToronto));

        // Same instant in Toronto config (19:00 Toronto local from 22:00 Toronto -> wrong example)
        // Use direct LA 19:00 to confirm off-hours.
        Instant sevenPmLa = ZonedDateTime.of(2026, 4, 15, 19, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant();
        assertFalse(laService.isDaytime(sevenPmLa));
    }

    @Test
    void shouldHandleSpringForwardDstBoundary() {
        // 2026-03-08 in US/Canada Eastern: clocks jump 02:00 -> 03:00.
        // 02:30 local does not exist; ZonedDateTime resolves it forward.
        BusinessHoursService service = svc(defaults());
        // 09:30 local on the spring-forward Sunday; weekdaysOnly=true -> still off-hours.
        assertFalse(service.isDaytime(torontoLocal(2026, 3, 8, 10)));

        // Monday after spring forward at 10:00 Toronto -> daytime.
        assertTrue(service.isDaytime(torontoLocal(2026, 3, 9, 10)));
    }

    @Test
    void shouldHandleFallBackDstBoundary() {
        // 2026-11-01 fall back: 02:00 -> 01:00. Sunday so weekdaysOnly forces false.
        BusinessHoursService service = svc(defaults());
        assertFalse(service.isDaytime(torontoLocal(2026, 11, 1, 10)));

        // Monday 2026-11-02 10:00 Toronto -> daytime.
        assertTrue(service.isDaytime(torontoLocal(2026, 11, 2, 10)));
    }

    @Test
    void shouldExposeHourLocalAccordingToTimezone() {
        BusinessHoursService service = svc(defaults());
        Instant t = torontoLocal(2026, 4, 15, 14);
        assertEquals(14, service.hourLocal(t));
    }

    @Test
    void shouldRespectClockForNowMethods() {
        BusinessHoursProperties p = defaults();
        // Set clock to a known daytime instant.
        Instant fixed = torontoLocal(2026, 4, 15, 14);
        BusinessHoursService service = new BusinessHoursService(p, Clock.fixed(fixed, ZoneId.of("UTC")));
        assertTrue(service.isDaytimeNow());
        assertEquals(14, service.hourLocalNow());
    }
}
