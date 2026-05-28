package com.fuba.automation_engine.persistence.entity;

/**
 * Normalized relationship type for a {@link PersonEntity}. Derived from the source
 * system's raw stage/role on every upsert (see
 * {@code PersonUpsertService.mapStageToKind}) so workflows can filter on a stable
 * vocabulary via {@code person.kind} instead of the customizable source stage string.
 *
 * <p>{@code UNKNOWN} is the safe default for any stage we don't recognise; an
 * unmapped stage is logged at WARN so new stages surface fast.
 */
public enum PersonKind {
    LEAD,
    AGENT,
    REALTOR,
    UNKNOWN
}
