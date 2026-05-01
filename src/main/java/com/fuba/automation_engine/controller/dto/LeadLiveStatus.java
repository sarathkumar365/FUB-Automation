package com.fuba.automation_engine.controller.dto;

/**
 * Outcome of the optional live FUB refresh on {@code GET /admin/leads/{id}/summary}.
 *
 * <ul>
 *   <li>{@link #LIVE_OK} — FUB responded; {@code livePerson} is populated.
 *   <li>{@link #LIVE_FAILED} — FUB call failed (HTTP or parse). Local snapshot
 *       still returned; UI shows the fallback banner.
 *   <li>{@link #LIVE_SKIPPED} — caller passed {@code includeLive=false}.
 * </ul>
 */
public enum LeadLiveStatus {
    LIVE_OK,
    LIVE_FAILED,
    LIVE_SKIPPED
}
