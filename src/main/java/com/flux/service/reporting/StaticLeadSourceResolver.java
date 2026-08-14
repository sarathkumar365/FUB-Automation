package com.flux.service.reporting;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default {@link LeadSourceResolver} — a fixed rule list for this brokerage.
 *
 * <p>The same channel arrives spelled several ways ("Instagram" / "Insta" /
 * "Intragram"), so reports would otherwise split one channel across rows.
 *
 * <p>Deliberately <em>not</em> a SQL view: another company's channels are different,
 * and adding one should not need a migration and a deploy. Reports group by the raw
 * string in SQL and fold here — 16 distinct values today, so the merge is free.
 * Replace wholesale by adding a {@code @Primary} {@link LeadSourceResolver}.
 */
@Component
public class StaticLeadSourceResolver implements LeadSourceResolver {

    /** FUB sends this literal string rather than an empty value (91 of 820 active leads, 2026-08-13). */
    private static final String FUB_UNSPECIFIED_SENTINEL = "<unspecified>";

    private static final Map<String, String> RULES = Map.ofEntries(
            Map.entry("facebook", "Facebook"),
            Map.entry("instagram", "Instagram"),
            Map.entry("insta", "Instagram"),
            Map.entry("intragram", "Instagram"),
            Map.entry("tiktok", "TikTok"),
            Map.entry("social media", "Social media"),
            Map.entry("manual add", "Manual Add"),
            Map.entry("mandeep dhesi", "Manual Add"),
            Map.entry("realtor.ca", "Realtor.ca"),
            Map.entry("investorguide", "InvestorGuide"),
            Map.entry("dhesirealestate.ca", "Website"),
            Map.entry("listing", "Listing"),
            Map.entry("referral", "Referral"),
            Map.entry("reference", "Referral"),
            Map.entry("open house", "Open house"));

    @Override
    public String resolve(String rawSource) {
        String key = rawSource == null ? "" : rawSource.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || FUB_UNSPECIFIED_SENTINEL.equals(key)) {
            return UNSPECIFIED;
        }
        return RULES.getOrDefault(key, OTHER);
    }
}
