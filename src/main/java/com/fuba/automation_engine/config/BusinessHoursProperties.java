package com.fuba.automation_engine.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Business-hours configuration used by the JSONata {@code now.*} scope keys
 * (see {@code BusinessHoursService}). Workflows can branch on time-of-day
 * via {@code branch_on_field} expressions like {@code now.isDaytime}.
 *
 * <p>Bound from {@code automation.business-hours.*} in application properties.
 * Editing requires a redeploy; a future Settings UI write API may make these
 * persistable per-tenant (Phase 7 / out of scope).
 *
 * <p>Daytime is the half-open interval {@code [startHour, endHour)} in
 * {@link #timezone}. {@code endHour=18} means 17:59 is daytime, 18:00 is
 * off-hours.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "automation.business-hours")
public class BusinessHoursProperties {

    /** IANA timezone ID. Default: {@code America/Toronto}. */
    @NotBlank
    private String timezone = "America/Toronto";

    /** Local hour at which daytime begins, inclusive. Default 9 (9 AM). */
    @Min(0)
    @Max(23)
    private int startHour = 9;

    /** Local hour at which daytime ends, exclusive. Default 18 (6 PM). */
    @Min(1)
    @Max(24)
    private int endHour = 18;

    /** When true, Saturday and Sunday are always treated as off-hours. */
    private boolean weekdaysOnly = true;
}
