package com.flux.service.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticLeadSourceResolverTest {

    private final LeadSourceResolver resolver = new StaticLeadSourceResolver();

    @Test
    void spellingAndCasingVariantsFoldIntoOneBucket() {
        assertThat(resolver.resolve("Instagram")).isEqualTo("Instagram");
        assertThat(resolver.resolve("Insta")).isEqualTo("Instagram");
        assertThat(resolver.resolve("Intragram")).isEqualTo("Instagram");
        assertThat(resolver.resolve("INSTAGRAM")).isEqualTo("Instagram");
        assertThat(resolver.resolve("  instagram  ")).isEqualTo("Instagram");
    }

    @Test
    void fubsUnspecifiedSentinelIsNotAnUnknownSource() {
        // FUB sends this as a literal string, not as an empty or absent value.
        assertThat(resolver.resolve("<unspecified>")).isEqualTo("Unspecified");
        assertThat(resolver.resolve("")).isEqualTo("Unspecified");
        assertThat(resolver.resolve("   ")).isEqualTo("Unspecified");
        assertThat(resolver.resolve(null)).isEqualTo("Unspecified");
    }

    @Test
    void anUnknownChannelStaysVisibleAsOther() {
        assertThat(resolver.resolve("LinkedIn Ads")).isEqualTo("Other");
    }

    @Test
    void everyKnownRawValueMapsToItsBucket() {
        assertThat(resolver.resolve("facebook")).isEqualTo("Facebook");
        assertThat(resolver.resolve("TikTok")).isEqualTo("TikTok");
        assertThat(resolver.resolve("Social media")).isEqualTo("Social media");
        assertThat(resolver.resolve("Manual Add")).isEqualTo("Manual Add");
        assertThat(resolver.resolve("Mandeep Dhesi")).isEqualTo("Manual Add");
        assertThat(resolver.resolve("Realtor.ca")).isEqualTo("Realtor.ca");
        assertThat(resolver.resolve("InvestorGuide")).isEqualTo("InvestorGuide");
        assertThat(resolver.resolve("dhesirealestate.ca")).isEqualTo("Website");
        assertThat(resolver.resolve("Listing")).isEqualTo("Listing");
        assertThat(resolver.resolve("Referral")).isEqualTo("Referral");
        assertThat(resolver.resolve("Reference")).isEqualTo("Referral");
        assertThat(resolver.resolve("Open house")).isEqualTo("Open house");
    }

    @Test
    void aReplacementResolverSlotsInWithoutTouchingCallers() {
        // Proves the seam: a future DB- or config-backed resolver implements one
        // method and every caller keeps working unchanged.
        LeadSourceResolver dynamic = raw -> "Zillow".equalsIgnoreCase(raw) ? "Zillow" : LeadSourceResolver.OTHER;

        assertThat(dynamic.resolve("zillow")).isEqualTo("Zillow");
        assertThat(dynamic.resolve("facebook")).isEqualTo(LeadSourceResolver.OTHER);
    }
}
