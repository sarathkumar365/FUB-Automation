# Reporting — value-query catalog (data-detective pass)

> **Status: findings.** A fresh-eyes analysis of the *whole* live `flux` DB (2026-06-25) to map
> what reporting value the existing data can deliver — beyond the Phase-2 accountability board.
> Grounds the reporting roadmap in what's actually answerable. Sibling: [data-model.md](./data-model.md)
> (how the data is written), [../phase-2-accountability-scoping.md](../phase-2-accountability-scoping.md)
> (the accountability slice + the ingestion accuracy gate).

## Data surface profiled (live DB, 2026-06-25)
Tables with signal: `persons` 1,323 (725 ACTIVE LEAD, 598 ACTIVE UNKNOWN), `processed_calls` 4,131,
`events` 2,367 (diary: `call.created` 1,303 / `person.created` 774 / `person.state_changed` 290),
`workflow_runs` 320, `automation_workflows` 7. Active leads are **100% populated** on
`source`/`type`/`stage`/`contacted`/`assignedUserId`; 92% have tags; 99% have a phone.
**Single segment today:** every active lead is `type='Buyer'`, `stage='Lead'` → type/stage slicing
has no value yet.

## Top findings (ranked by impact × ease)

### 1. Facebook is the lead leak — and the owner pool is NOT the cause
The intuitive story ("leads pile on the owner and rot") is **wrong**. The owner contacts his leads
well; the leak is **Facebook leads, wherever they land.**

| Source | Leads | % on owner pool | % contacted (FUB flag) |
|---|---|---|---|
| **facebook** | 308 | 34% | **51%** |
| Instagram | 197 | 97% | **92%** |
| Manual Add | 66 | 98% | 95% |
| Realtor.ca | 24 | 96% | 58% |

Instagram is 97% owner-held yet 92% contacted — the owner is not a graveyard. **Facebook is the
biggest source (308) and worst-handled (51%) → ~150 uncontacted FB leads.** Either FB lead quality
is junk (→ stop paying for volume) or FB handling/routing is broken (→ fix the workflow). Highest-
value open question in the dataset. *(Confidence high on the gap; `contacted` is FUB-defined — dig
into what channels it counts.)*

### 2. A concrete "no lead missed" worklist exists today — 244 leads
FUB's own `contacted=0` flag marks **244 active leads never contacted, ~210 older than 7 days.**
This is the accountability board's reason-to-exist, available now and **downtime-robust** (finding 3).
By agent: Mandeep 139 (107 >7d), Shahrukh 21, Gurbir 16, Navjot 16, Karanjot 12, Arjun 11, Chirag 9…

### 3. Snapshot flags AND the event timeline are both trustworthy going forward (hosting resolves the gate)
**Updated 2026-07-02.** `source`, `assignedUserId`, and **`contacted`** ride the *person snapshot* →
downtime-robust, believe always. Call-derived metrics were historically uptime-gated (see
[data-truths §2.1](./data-truths.md#21-completeness-is-uptime-bound--healed-by-continuous-hosting-not-a-build-blocker)),
but **the app is now hosted and runs continuously**, so the event stream is complete going forward and
the forward 24h/7d windows v1 uses are fully covered. Cross-check still holds: of 244 FUB-"uncontacted"
leads we hold an outbound call for **113** → FUB `contacted` (multi-channel/manual) and our "called"
(one channel) measure different things. **Report both as distinct columns; never conflate.** And the
`events` diary reconstructs a full per-lead timeline (assignment changes, contacted flips, calls — all
timestamped, [data-truths §2.9](./data-truths.md#29-the-events-diary-already-reconstructs-a-full-per-lead-timeline--this-is-buildable-now)),
so **timeline-correct attribution is buildable now** — no reconcile job required.

## The catalog — query families that create value

### A. Coverage & accountability — *"no lead is missed"* (grain: active lead)
- **Uncontacted leads** (FUB flag) → 244 now. *Robust.*
- **Assigned-but-not-called** (`source_user_id = assignedUserId`) → the red list. *Uptime-gated.*
- **Aging / SLA breach** — uncontacted bucketed by age (0–24h / 1–3d / 3–7d / 7d+) → ~210 over 7d.
- **Per-agent drill** to actual lead name + phone to chase *(mask PII in shared views)*.

### B. Acquisition & source ROI — *leads by source → agent → called*
- **Lead mix by source** → facebook 308, Instagram 197, Manual Add 66, Realtor.ca 24…
- **Source → contact rate** → FB 51% vs IG 92% (the money query).
- **Source → assigned agent** routing crosstab → FB spreads to agents (34% owner), IG concentrates (97%).
- **Source → connect rate** (≥30s talk) — possible but *uptime-gated*.
- **New leads/day by source** (trend) — from `events.person.created`.

### C. Speed & responsiveness — *time-to-first-call*
- **Time-to-first-call** = `person.created` → first outbound call, per lead/agent/source.
  *Caveat: `created`=first-seen-by-us + uptime-gated → directional, not SLA-grade until ingestion fixed.*
- **Leads waiting now** — uncontacted, ranked by hours since arrival. *Robust.*

### D. Agent performance & activity (grain: agent × window; all *uptime-gated*)
- Calls placed / connected / distinct leads dialed per agent (e.g. Navjot ~219/wk dominates).
- **Connect rate** (≥30s ÷ outbound) per agent — quality, not just volume.
- Talk-time distribution (avg non-zero ~72s; p50/p90 via `percentile_cont`).
- **Activity heatmap** by hour/day — staffing & coverage gaps.

### E. Lead lifecycle & routing — *from the `events` diary*
- **Reassignment flows** — who routes to whom; **leads bouncing back to the owner** (6→owner observed
  in one day) = agents declining leads.
- **Per-lead timeline** — full history (created → reassigned → called → noted); strong for disputes.
- Stage funnel — *flat today* (all `stage='Lead'`); no value until stages are used in FUB.

### F. Operational reliability — *(Phase 1 already covers most)*
- Workflow run health → 295 COMPLETED / 15 CANCELED / 6 FAILED.
- **Ingestion uptime monitor** — the local-vs-FUB gap detector; alert when a downtime window opens so
  reporting integrity is known. Valuable internal query in its own right.

### G. Data quality — *findings + caveats on everything above*
- **Source normalization** — `Instagram`/`Insta`/`Intragram`, `Social media`/`TikTok` are messy
  free-text → collapse before reporting.
- **`kind` classifier broken** — 598 ACTIVE persons UNKNOWN (incl. ~540 agents). (See scoping Cat 2.)
- **`'0'` sentinel** calls (person-less) → exclude.
- **`contacted` vs `called` divergence** (244 vs 113) → two distinct columns.

## Cross-cutting caveats (apply to every query above)
1. **Uptime (mostly resolved 2026-07-02)** — `processed_calls`/`call.created` are complete going forward
   under continuous hosting; forward 24h/7d windows are fully covered. Only *pre-hosting historical*
   windows undercount, and those are out of v1 scope (data-truths §2.1). FUB-snapshot fields remain
   downtime-robust regardless.
2. **Owner pool** (`uid=1`) — **not** segregated: rendered as a normal agent (data-truths §2.2).
   Report 2's timeline-correct attribution prevents the per-agent distortion the old segregation guarded
   against.
3. **Single segment** — all leads Buyer/Lead → type/stage slicing is dead weight today.
4. **Mirror incompleteness** — orphan leads (called but absent from the mirror) fall out of
   timeline-correct attribution; surface as a count. Shrinks going forward under continuous hosting.

## Recommendation — what to ship first
**Updated 2026-07-02 — v1 scope locked to two reports on a lead-timeline substrate** (see
[phase-2-accountability-scoping.md](../phase-2-accountability-scoping.md) and data-truths §6):
1. Build the **lead-timeline layer** (on-read views over `events`: per-lead history + holder-intervals).
2. **Report 1 — Source → leads → contacted (any channel)** — coverage, on snapshot flags. Answers
   "are we missing leads, and where do good leads die?" (the Facebook-51%-vs-Instagram-92% leak).
3. **Report 2 — Assigned → contacted, timeline-correct** — accountability, attributing each contact to
   the holder-at-the-time. Now buildable in v1 (not deferred) because the timeline + continuous hosting
   remove the old call-completeness blocker.
Windows 24h/7d, forward-only. The reconcile job (Phase 2b) is optional, not a prerequisite.

## Report-flow archetypes (industry patterns) — mapped by build tier
The manager UX is built from a small set of standard analytics **flows** (interaction shape +
report archetype), not bespoke pages. Naming them lets us decide which earn a build. The first
concrete one designed is a **drill-down funnel** (Source → Agent → Outcome) using **click-to-drill /
overview-detail** — see the mockup in chat (2026-06-26).

**Interaction mechanics (reusable across reports):** overview→detail / click-to-drill · progressive
disclosure · cross-filtering · global vs. local filters · brush/zoom · tooltip-detail / modal deep-dive.

**Report archetypes, mapped to our data** (🟢 reliable now · 🟡 have it, accurate only inside an
app-uptime window — gated on the reconcile job · 🔴 not captured yet):

| Archetype | What it is | Our example | Data |
|---|---|---|---|
| Funnel | stage-by-stage drop-off | source → agent → contacted → connected → *converted* | 🟢🟡 to connected; 🔴 converted |
| Segmentation / breakdown | slice a metric by a dimension | leads by source / agent / tag | 🟢 |
| Leaderboard / rep scorecard | rank people vs. each other / goal | agents by calls, connect-rate | 🟡 activity; 🔴 outcome/quota |
| Worklist / exception | "who needs action now" | the 244 uncontacted, aging | 🟢 strongest today |
| Cohort / retention | group by entry period, track over time | leads arrived this week → contacted within 24/48h | 🟡 (uptime + no true intake date) |
| Velocity / response-time | speed between stages | time-to-first-call per agent/source | 🟡 weak (no reliable assigned-at) |
| Attribution | credit outcomes back to a source | which source produces closings | 🔴 needs deals |
| Pipeline / forecast | value/count by deal stage | leads by stage, expected closings | 🔴 single stage, no deals |
| Trend / time-series | a metric over time | leads/day, calls/day | 🟢 volume; 🟡 calls |
| Anomaly / alert (push) | notify when a threshold trips | "agent X: 10 uncontacted >48h" | 🟢 data; needs scheduler (push infra) |

**The three build tiers (decide the roadmap around these):**
1. **Buildable now, reliable (the v1 menu):** segmentation, worklist/exception, the drill-down funnel
   *to contact*, lead-volume trends **plus timeline-correct assigned→contacted attribution** — the
   last unlocked 2026-07-02 by the `events`-diary timeline (data-truths §2.9) + continuous hosting.
2. **Trustworthy forward under continuous hosting** (was "gated on the reconcile job"): leaderboard /
   scorecard, velocity, call trends — call-derived, complete for forward 24h/7d windows now that the
   app runs continuously. The reconcile job (Phase 2b) is now *optional* — only for pre-hosting history.
3. **Blocked until new capture:** full funnel-to-**conversion**, **pipeline/forecast**, **attribution
   to outcomes**.
   All three need FUB **appointments/deals** ingested — so that one capture investment **unlocks three
   archetypes at once**, the strongest argument for prioritizing it right after the reconcile job.

**Conversion is the recurring wall.** Every "outcome" flow (tier 3) dead-ends on the same gap: we
have no deals/appointments/closings and every lead is `stage='Lead'`. v1 funnels should **end at
*connected*** and label conversion "coming soon (needs FUB appointments/deals)."
