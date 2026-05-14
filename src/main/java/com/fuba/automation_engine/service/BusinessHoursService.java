package com.fuba.automation_engine.service;

import com.fuba.automation_engine.config.BusinessHoursProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;

/**
 * Pure business-hours evaluator. Single-region for v1 — driven by
 * {@link BusinessHoursProperties}.
 *
 * <p>Daytime semantics: {@code [startHour, endHour)} in the configured
 * timezone, plus the optional weekday-only gate. DST handled by
 * {@link ZonedDateTime} naturally (the local hour is what we care about).
 */
@Service
public class BusinessHoursService {

    private final BusinessHoursProperties properties;
    private final Clock clock;

    public BusinessHoursService(BusinessHoursProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** True when the given instant falls within configured business hours. */
    public boolean isDaytime(Instant instant) {
        ZonedDateTime local = toLocal(instant);
        if (properties.isWeekdaysOnly() && isWeekend(local.getDayOfWeek())) {
            return false;
        }
        int hour = local.getHour();
        return hour >= properties.getStartHour() && hour < properties.getEndHour();
    }

    /** True for the engine's current clock instant. */
    public boolean isDaytimeNow() {
        return isDaytime(clock.instant());
    }

    /** Local hour-of-day (0–23) for the given instant in the configured timezone. */
    public int hourLocal(Instant instant) {
        return toLocal(instant).getHour();
    }

    /** Local hour-of-day for the engine's current clock instant. */
    public int hourLocalNow() {
        return hourLocal(clock.instant());
    }

    private ZonedDateTime toLocal(Instant instant) {
        return instant.atZone(ZoneId.of(properties.getTimezone()));
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
