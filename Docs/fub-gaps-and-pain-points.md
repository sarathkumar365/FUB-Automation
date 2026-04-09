# Follow Up Boss — Real Gaps & Pain Points

Scraped from G2, Capterra, TrustRadius, SoftwareFinder, Reddit, Google Play, HousingWire, inboundREM, The Pro Tool Kit, FUB Help Center, and real estate community forums. All complaints sourced from real users — nothing fabricated.

Last updated: 2026-04-09

---

## 1. Zillow Acquisition & Data Privacy (CRITICAL — Industry-Wide)

| # | Problem | Severity |
|---|---------|----------|
| 1 | Zillow's Nov 2025 privacy policy introduced "Mutual Customer Data" — if a consumer in an agent's FUB database also has a Zillow account, Zillow can access and contact them directly | Critical |
| 2 | Since virtually every US homebuyer uses Zillow, this effectively exposes most of an agent's CRM database | Critical |
| 3 | Top industry coaches (Tom Ferry, Jared James, Jimmy Mackin) publicly told agents to leave FUB | Critical |
| 4 | Zillow can potentially share enriched data with competing Zillow Pro members | Critical |

**Opportunity:** Agents are actively looking for FUB alternatives or middleware that protects their data. A privacy-first automation layer could be a major differentiator.

---

## 2. Automation & Action Plan Limitations

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | Only the first text in a drip campaign is automated — all follow-up texts must be sent manually or via expensive third-party tools (CallAction, TextBetty) | ISA teams, power users |
| 2 | No batch/bulk texting capability at all | All agents |
| 3 | Action plan emails capped at 4 per day per contact | Nurture-heavy teams |
| 4 | Action plans cannot be mass-applied; automations won't trigger more than 100 times at once | Large database teams |
| 5 | Text messages do NOT auto-send when action plans are triggered by automations (only works via lead flow page) | Automation builders |
| 6 | Automations are linear and basic — no behavior-triggered or adaptive branching | Power users |
| 7 | Automatic actions sometimes silently fail to activate | All users |
| 8 | Phone call duration threshold (2.5 min) is rigid — no way to customize | ISAs, callers |
| 9 | No way to stop a campaign automatically with a stage change | Database admins |
| 10 | Action plans are confusing to set up and verify they're running correctly | Most users |

**Opportunity:** THIS IS THE CORE OF YOUR PROJECT. FUB's native automation is shallow. An intelligent automation engine that handles branching logic, bulk operations, and reliable execution would solve the #1 power-user complaint.

---

## 3. Mobile App (Most Complained-About Feature)

| # | Problem | Platform |
|---|---------|----------|
| 1 | Android app called "an absolute mess" — useless alerts, no sorting, list-only calendar | Android |
| 2 | Smart Lists don't exist on mobile | Both |
| 3 | Calendar bug — appointments added to wrong dates | Both |
| 4 | No copy/paste, no dialer, no phone contact sync on mobile | Android |
| 5 | Cannot send pictures or files through the app | iOS |
| 6 | Cannot select which phone number to call/text with | Both |
| 7 | Email templates cannot be modified on mobile | Both |
| 8 | App crashes on login for some users | Android |
| 9 | Android version significantly more limited than iOS | Android |
| 10 | "Developers don't care and clearly don't use it" — Google Play reviewer | Android |

**Opportunity:** A companion mobile experience or a mobile-first automation dashboard could fill this gap without rebuilding the entire CRM.

---

## 4. Reporting & Analytics

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | Automated action plan activities are EXCLUDED from reporting metrics | Team leads |
| 2 | Dashboard excludes marketing emails/texts, action plan emails, and batch emails | Brokers |
| 3 | Reporting described as "clunky" — good for employee oversight, not deep analysis | Data-driven teams |
| 4 | No lender-role reporting (only Owner, Admin, Agent) | Lender partners |
| 5 | Deleting contacts permanently destroys reporting data | All users |
| 6 | No audit trail — can't see who added/removed tags or changed contact info | Compliance, brokers |
| 7 | Competitors (BoomTown, Sierra, REW) display user behavior much more cleanly | All users |

**Opportunity:** A reporting/analytics layer built on FUB's API data could offer the deep insights FUB doesn't provide natively. Especially valuable for brokers managing teams.

---

## 5. Pricing Complaints

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | $69-99/user/month with no volume discounts — a 20-person team pays $1,380-1,980/month | Mid-size teams |
| 2 | No solo agent plan (minimum 3 agents) | Solo agents |
| 3 | Built-in dialer costs extra $39/user/month on top of base | Budget teams |
| 4 | At 15-30 users, competitors (KVCore, CINC) negotiate as low as $15/user | Large brokerages |
| 5 | Price increases with each version change (V2 raised prices) | Existing customers |

**Opportunity:** A more affordable automation add-on that delivers premium-tier features at a fraction of FUB's upsell cost.

---

## 6. Lead Routing & Reassignment

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | No automatic lead reassignment if an agent doesn't respond within a timeout | Team leads |
| 2 | No customizable timeout settings for follow-up windows | Brokers |
| 3 | No instant-connect call features (speed-to-lead calling) | High-volume teams |
| 4 | No bulk-reassign API endpoint — must batch within rate limits | Developers |
| 5 | Basic routing insufficient for large teams | Brokerages 15+ agents |

**Opportunity:** Intelligent lead routing with timeout-based reassignment, round-robin with performance weighting, and speed-to-lead automation — all buildable via the API.

---

## 7. Contact & Data Management

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | No way to link related contacts at the profile level (only within deals) | Transaction coordinators |
| 2 | No audit trail for contact changes (who changed what, when) | Compliance, brokers |
| 3 | Data doesn't sync properly between different sections of FUB | All users |
| 4 | Cannot view 2 client records simultaneously | Agents |
| 5 | Merger/account transitions described as "a nightmare" | Teams restructuring |
| 6 | Smart Lists capped at 2,000 leads per list | Large databases |

**Opportunity:** A contact intelligence layer that enriches, links, and audits contact data beyond FUB's native capabilities.

---

## 8. Agent Autonomy & Permissions

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | Agents can't modify their own settings without admin approval | Independent agents at brokerages |
| 2 | Agents lack autonomy to market to their own uploaded contacts | Agents with personal SOI |
| 3 | FUB acknowledged this complaint but hasn't fixed it | Industry-wide |

**Opportunity:** A layer that gives agents self-service automation controls within boundaries set by the broker.

---

## 9. API & Developer Limitations

| # | Problem | Impact |
|---|---------|--------|
| 1 | Rate limited to 1,000 requests per 10 minutes (sliding window) | Slows bulk operations |
| 2 | No SCIM 2.0 support — user provisioning is manual or custom REST | Enterprise IT friction |
| 3 | No bulk-reassign endpoint | Forces batching workarounds |
| 4 | No user-lifecycle webhooks (provisioning/deprovisioning events) | Sync gaps with HR systems |
| 5 | Webhook limit of 2 per event per system | Requires fan-out architecture |
| 6 | Different endpoints have different (stricter) rate limit contexts | Unpredictable throttling |

**Opportunity:** Your automation engine already works within these constraints. Understanding them deeply = competitive moat for anyone trying to replicate what you build.

---

## 10. SMS & Communication Issues

| # | Problem | Who's Affected |
|---|---------|----------------|
| 1 | Carrier filtering blocks messages without clear feedback | All texting agents |
| 2 | Identical texts to multiple contacts flagged as spam | Bulk outreach |
| 3 | Texts with attachments, emojis, links, or vCards more likely filtered | Marketing agents |
| 4 | Texts from clients display in odd/confusing formatting | All users |
| 5 | No WhatsApp or social media communication integration | International/modern agents |

---

## 11. Missing Platform Capabilities

| # | What's Missing | Available In Competitors |
|---|----------------|------------------------|
| 1 | No IDX website or landing pages | KVCore, Sierra, Chime |
| 2 | No transaction management | SkySlope, Dotloop |
| 3 | No print marketing materials | KVCore |
| 4 | No auto-newsletter generation | Mailchimp integration needed |
| 5 | No video email from within FUB | BombBomb integration needed |
| 6 | AI features described as "often not accurate" | Still early |
| 7 | No DNC list scrubbing in dialer | Compliance risk |
| 8 | Property view tracking is weak — can't see repeat visits | Sierra, REW |

---

## 12. Support & Onboarding

| # | Problem |
|---|---------|
| 1 | Support helpful during setup but "less helpful for troubleshooting" |
| 2 | Support response times getting slower as company grows |
| 3 | No live chat support, no 24/7 availability |
| 4 | "Serious learning curve" despite marketing as intuitive |
| 5 | Demo quality depends on which sales rep you get |
| 6 | Cancellation process is difficult |

---

## Priority Matrix — What Your Automation Engine Can Solve

| Gap | Can We Solve Via API? | Impact | Effort |
|-----|----------------------|--------|--------|
| Automation branching & adaptive drips | **YES** | Very High | Medium |
| Bulk texting workarounds | **YES** (with carrier compliance) | High | Medium |
| Lead reassignment on timeout | **YES** | High | Low |
| Smart list limitations | **YES** (external logic) | High | Medium |
| Reporting & analytics layer | **YES** (read API data) | High | High |
| Contact audit trail | **YES** (webhook logging) | Medium | Medium |
| Contact relationship linking | **Partial** (custom fields) | Medium | Low |
| Speed-to-lead calling | **Partial** (Twilio + FUB API) | High | High |
| Agent self-service controls | **YES** (middleware layer) | Medium | Medium |
| Data privacy / Zillow concerns | **Indirect** (data portability tools) | Very High | Medium |

---

## Sources

- [Capterra Reviews](https://www.capterra.com/p/130020/Follow-Up-Boss/reviews/)
- [G2 Reviews](https://www.g2.com/products/follow-up-boss/reviews)
- [TrustRadius](https://www.trustradius.com/products/follow-up-boss/reviews)
- [SoftwareFinder](https://softwarefinder.com/crm/follow-up-boss/reviews)
- [inboundREM 2025 Review](https://inboundrem.com/follow-up-boss-pros-and-cons/)
- [The Pro Tool Kit 2026](https://theprotoolkit.com/follow-up-boss-review-2026/)
- [Google Play Store](https://play.google.com/store/apps/details?id=com.followupboss.fubandroidclient)
- [FUB Help Center](https://help.followupboss.com/)
- [FUB API Docs](https://docs.followupboss.com/)
- [HousingWire](https://www.housingwire.com/articles/zillow-follow-up-boss-privacy-changes/)
- [Notorious ROB](https://notoriousrob.substack.com/p/follow-up-boss-changes-privacy-policy)
- [SelectHub](https://www.selecthub.com/p/real-estate-crm-software/follow-up-boss/)
- [Esgrow Alternatives](https://getesgrow.com/blog/follow-up-boss-alternatives-2026)
- [Sierra Interactive Comparison](https://www.sierrainteractive.com/our-solutions/follow-up-boss-competitor-comparison/)
