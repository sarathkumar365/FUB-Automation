package com.fuba.automation_engine.persistence.entity;

public enum LeadStatus {
    ACTIVE,
    ARCHIVED,
    // Placeholder kept to match the V14 CHECK constraint. No code path sets MERGED today —
    // FUB does not currently surface a "two people merged" signal to us, so this value is
    // reserved for a future explicit merge handler rather than implicit dedupe.
    MERGED
}
