package com.flux.controller.dto;

/**
 * The contact breakdown for a group of leads. The three states are mutually exclusive per
 * lead and always sum to {@code leads}.
 *
 * <p>Shared by both reports, so it lives in its own type rather than inside either one.
 *
 * @param spoke     a real conversation — an outbound call over the configured threshold,
 *                  made by whoever held the lead at that moment
 * @param attempted dialled without connecting, or a touch the CRM recorded that we cannot
 *                  verify. Effort, not contact
 * @param nothing   neither
 */
public record ContactCountsDto(long leads, long spoke, long attempted, long nothing) {}
