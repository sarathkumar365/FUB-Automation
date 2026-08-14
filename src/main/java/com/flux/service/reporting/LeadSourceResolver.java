package com.flux.service.reporting;

/**
 * Folds FUB's free-text {@code source} into the buckets reports group by.
 *
 * <p>An interface because the rules are <em>company data, not schema</em>: today they
 * are a static list ({@link StaticLeadSourceResolver}), but a per-company table or an
 * admin-editable mapping should be able to replace it without touching any caller.
 * To swap, add another {@code @Component} implementing this and mark it
 * {@code @Primary} — reports depend on this type, never on the implementation.
 */
public interface LeadSourceResolver {

    /** Bucket for anything the rules don't recognise. Never dropped — a new channel must stay visible. */
    String OTHER = "Other";

    /** Bucket for null, blank, or a source the CRM explicitly marks as unset. */
    String UNSPECIFIED = "Unspecified";

    /** Maps a raw CRM source string to its display bucket. Never returns null. */
    String resolve(String rawSource);
}
