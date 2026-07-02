# Reporting — data truths (the semantic layer, in prose)

> **Status: canonical findings / semantic-layer spec.** This is the single source for **what the
> reporting numbers mean** and **the data-quality truths required to read them correctly** — the
> business meaning that is *not present in the schema* for any tool or model to discover. The SQL
> views encode this; a future NL/agent interface ([RD-014](../../repo-decisions/RD-014-reporting-query-architecture.md))
> consumes it as context. Everything here is **verified against the live `flux` DB and the FUB API
> (2026-06-25/26)**, not assumed. Siblings: [data-model.md](./data-model.md) (how the data is
> written), [value-query-catalog.md](./value-query-catalog.md) (what to build on it),
> [../phase-2-accountability-scoping.md](../phase-2-accountability-scoping.md).

## 1. Metric definitions (frozen — every report reuses these)
| Term | Definition |
|---|---|
| **Active assigned lead** (denominator) | `persons` where `kind='LEAD' AND status='ACTIVE'` and `assignedUserId` present. Nothing else. (725 today.) |
| **Called / Attempted** | ≥1 **outbound** call (`processed_calls.is_incoming=false`) by the **assigned agent** (`source_user_id = (assignedUserId)::bigint`). Same FUB userId namespace — the join is valid. |
| **Connected** | a Called row with **`duration_seconds >= 30`** (tunable). `outcome` is useless here — free-text, 91% blank, no positive value. |
| **Contacted** (FUB flag) | `person_details.contacted = '1'` — FUB's own multi-channel, FUB-computed flag. **NOT the same as Called** (see §2.5). Downtime-robust. |
| **Lead-initiated** | ≥1 **inbound** call (`is_incoming=true`) from the lead — a separate signal, never credited as the agent calling. |
| **Red** | active assigned lead with **no qualifying agent-outbound call** in the active window. *(Trust gated — see §2.1.)* |
| **Group key** | **`assignedUserId`** (stable id); **display `assignedTo`** (name). Never group by name. |

## 2. Data-quality truths (the things NOT in the schema)
Each is a fact about how this brokerage / pipeline works that no schema-probe or row-sample reveals.

### 2.1 Completeness is NOT healed by hosting — reconcile is mandatory (ingress-bound accuracy gate)
> **CORRECTION 2026-07-02 (audit) — supersedes the "healed by continuous hosting / reconcile optional"
> reframe below.** A live FUB-API audit disproved the "hosted ⇒ complete" premise. Measured against
> ground truth *today*: recent call capture is **~41%** (30 of 73 FUB outbound calls in a 06-24/25 slice
> are absent from local), whole days inside the window have **zero** webhooks of any type (06-27, 06-28,
> 07-01), and the owner's "not reached" list is **~40% false reds** (10/25 sampled leads were actually
> called per FUB; e.g. lead 15293 called 4× on 06-25/26 — the hosted era — with 0 local rows).
> **Root cause:** it is **not** app uptime and **not** ingestion (4,362 webhooks → 4,395 processed_calls,
> ≈1:1; missing calls are byte-identical in shape to captured ones). The FUB webhook is registered to an
> **ephemeral Cloudflare quick tunnel** (`views-announcements-walk-carter.trycloudflare.com`; `.env`
> `PUBLIC_BASE_URL` is empty), whose hostname changes on every `cloudflared` restart while FUB holds one
> static registration. FUB delivery is **at-most-once with no replay**, so every event fired while the
> URL is stale is **lost permanently**. Continuous *app* hosting cannot fix a fragile *ingress* + no
> backfill. **Consequence: the reconcile/backfill job is MANDATORY, not optional** — it is the only thing
> that heals dropped webhooks. Snapshot flags (`source`/`assignedUserId`/`contacted`) still match FUB
> exactly and remain downtime-robust; the gap is call-derived metrics (Report 2). Fix = stable public
> URL **and** a scheduled FUB `/v1/calls`+`/v1/people` since-last-sync reconcile. See §2.1-orig below for
> the earlier (now-disproven) reasoning, retained for history.

### 2.1-orig Completeness is uptime-bound — healed by continuous hosting (NOT a build blocker) — DISPROVEN 2026-07-02
**Reframed 2026-07-02 (was "THE ACCURACY GATE / build-blocker"); this reframe was itself DISPROVEN the
same day — see the CORRECTION above.** Verified 2026-06-25: when the app
is running, **local == FUB exactly, 0% variance** (window 06-25 13:00-21:00 UTC: all 7 agents'
outbound/connected/distinct-lead counts identical in both sources). **Ingestion is correct — no webhook
filtering, no mapping bug; the numbers are right.** The *only* source of inaccuracy is **coverage**:
the app was historically not run 24/7, and webhooks that fired while it was down are lost permanently
(FUB delivers once, no retry). Historical evidence:
- **Zero** local calls for **06-20 → 06-23** (FUB had 142 / 91 / 225 / 193).
- Best recent full day 06-24: local **214** vs FUB **400** outbound (~54%). All-time (pre-hosting):
  local **4,120** vs FUB **129,156**.

**Resolution (decided 2026-07-02): the app is hosted and runs continuously**, so **going forward the
event stream is complete** — no gap to heal. Two scoping decisions make this a non-issue for v1:
1. **Historical (pre-hosting) data is out of v1 scope.** We report only forward, over short recency
   windows (**24h / 7d**), which sit entirely inside continuous uptime.
2. A **reconcile/backfill job** (FUB `/v1/calls`+`/v1/people` since last sync) was demoted to optional
   here — **REVERSED by the CORRECTION above: it is MANDATORY.** The 2026-07-02 audit found active call
   loss in the hosted window (~41% capture), so short forward windows are **not** complete.

So `processed_calls`-derived metrics were claimed trustworthy for any hosted 24h/7d window — **the audit
showed this is false** (~40% false-red persists in the hosted era). The "~85% false-red" was blamed on
pre-hosting downtime + naive attribution; timeline-correct attribution (§6) fixes the naive part, but the
**dropped-webhook part is real and ongoing** and only the reconcile job heals it.

### 2.2 Owner / default-assignee pool (`assignedUserId = 1`) — legitimate FUB state, segregate it
`uid=1` ("Mandeep Dhesi", FUB owner) holds **~486-498 of ~725 active leads (68%)**. Verified via FUB
API: these are genuinely `assignedUserId=1, claimed=true` in FUB now, with FUB `created` dates in
**2023** — old, long-claimed leads (our `created_at=2026-04-17` is just backfill/first-seen day). And
it's **ongoing, not legacy**: new leads still default to the owner at **80% (Apr) → 52% (May) → 56%
(Jun)**, and **72% of yesterday's intake (38/53)** went to the owner. **Not a sync bug** — reassignment
sync works (30 `assignedUserId`-change events; leads spread across 11 agents). **Handle as a display
decision. **Settled 2026-07-02: render `uid=1` as a normal agent** — whatever leads sit with him now
are shown as his, no separate bucket, no exclusion. (The earlier "segregate / exclude the owner pool"
option was dropped: he *is* an agent, and Report 2's timeline-correct attribution (§6) already prevents
the false-red problem by crediting calls to the holder-at-the-time, not a naive current-holder join —
so the "~70% of all leads RED, mostly false" failure mode doesn't arise.)

### 2.3 `source_person_id = '0'` — FUB's person-less sentinel, not our default
191 calls carry `'0'`. The write path stores `null` when FUB sends null; `'0'` appears **only when
FUB sends `0`** — its id for a call with no matching contact (overwhelmingly **SKIPPED inbound** calls
from numbers not in the FUB address book). **Treat `'0'` as unlinked/null; never join it as a lead.**
*(Optional cleanup: normalize `0 → null` on write.)*

### 2.4 `kind` classifier is broken — `kind` is untrustworthy system-wide
`mapStageToKind()` does a case-insensitive **exact** match (`"agent" → AGENT`). FUB sends
`stage="Real Estate or Mortgage Agent"`, so **all 540 agents fall to `kind=UNKNOWN`; 0 rows are
`kind=AGENT`.** Does **not** corrupt the lead denominator (UNKNOWNs aren't active leads) but means
`kind` cannot be trusted to identify agents. **Separate bug ticket** (tokenize the stage; look for an
`agent`/`lead` word) — pairs with the onboarding-ingest pipeline (also closes Issue #19).

### 2.5 `contacted` (FUB flag) ≠ `called` (our calls) — report both, never conflate
FUB's `contacted` is **multi-channel and FUB-defined** (likely texts/emails/manual too) and rides the
person snapshot → **downtime-robust**. Our `called` is one (gappy) channel. They diverge: of 244
`contacted=0` leads, **113 have an outbound call in our data**; of 482 `contacted=1`, 423 do. So they
measure different things. Surface as **two distinct columns**.

### 2.6 Agent identity is ~1:1 — exceptions are tiny and FUB-side
Only 2 ids split: uid 31 ("Arjun Ahluwalia"/"Arjun Singh Ahluwalia" = a FUB name edit) and uid 30 (an
ISA pond account also labelled "Mandeep Dhesi"). We snapshot `assignedTo` verbatim from FUB.
**Group by `assignedUserId`, pick one canonical name per id** — fully resolves it.

### 2.7 `source` is messy free-text — normalize to the canonical set (part of the semantic layer)
Raw values seen on active leads (live scan 2026-07-02): `facebook` (359), `Instagram` (199), blank (90),
`Manual Add` (66), `Realtor.ca` (24), `InvestorGuide` (15), `Social media` (10), `TikTok` (10),
`dhesirealestate.ca` (4), `Listing` (3), `Referral` (2), `Insta` (1), `Intragram` (1), `Open house` (1),
`Mandeep Dhesi` (1), `Reference` (1). **Canonical bucket set (locked 2026-07-02, tunable)** — the
raw→canonical mapping is encoded in the semantic-layer view and read by Report 1's top axis:

| Canonical bucket | Raw values folded in |
|---|---|
| **Facebook** | `facebook` |
| **Instagram** | `Instagram`, `Insta`, `Intragram` |
| **TikTok** | `TikTok` |
| **Social media** | `Social media` |
| **Manual Add** | `Manual Add`, `Mandeep Dhesi` |
| **Realtor.ca** | `Realtor.ca` |
| **InvestorGuide** | `InvestorGuide` |
| **Website** | `dhesirealestate.ca` |
| **Listing** | `Listing` |
| **Referral** | `Referral`, `Reference` |
| **Open house** | `Open house` |
| **Unspecified** | blank / null |

### 2.8 Mirror is materially incomplete vs FUB (completeness = webhook delivery)
**59%** of person-linked calls are **orphans** (reference a lead absent from our `persons` mirror).
In the last 7 days, **178 of 255 distinct leads dialed (70%) were absent from the mirror entirely**
(not archived, not mis-typed). ~1,382 calls are unmapped (no person/agent/direction), incl. 185
`TASK_CREATED`. Same root cause as §2.1 (downtime) + no historical backfill. A reconcile/backfill job
heals it; until then, surface as caveats/counts.

### 2.9 The `events` diary already reconstructs a full per-lead timeline — this is buildable NOW
**Reframed 2026-07-02 (supersedes "person-timeline is a deferred future feature").** The `events` table
is an event-sourced diary that captures, timestamped, everything that happens to a lead — verified
against live data:
- **`call.created`** (1,555 rows) — `{userId, personId, createdAt, isIncoming, duration, outcome}`:
  who called which lead, when, direction, duration. `userId` shares the `assignedUserId` namespace, so
  "did the holder call this lead?" is an id-to-id comparison, not a guess.
- **`person.state_changed`** (390 rows) — `{changed_fields, previous, current}`: **reassignments**
  (`assignedUserId`/`assignedTo` before→after, ~66 rows), **contacted flips** (`contacted` 0→1, 61
  rows), plus source/tags/stage — each timestamped.
- **`person.created`** (844 rows) — anchors the initial holder + source + contacted + tags at first-seen.

**Consequence — attribution is exact, not guessed.** Holding both the assignment timeline and the call
timeline, we can reconstruct **who held a lead at any instant** and credit each call/contact to the
holder at the moment it happened — immune to later reassignment. This is the substrate for the
accountability report; see [§6 the lead-timeline layer](#6-the-lead-timeline-layer--the-reporting-substrate).

**Caveats:** (1) `person.created` = first-seen-by-us, not FUB intake — so *time-to-contact* is measured
from first-seen (attribution unaffected; only SLA-grade timing is soft). (2) **assigned-at is reliable
for post-ingestion changes** (every reassignment we observed is timestamped), *not* for the
pre-ingestion baseline. (3) Completeness is gated on the webhook ingress + reconcile job (§2.1
CORRECTION; Phase 2c) — continuous *app* hosting does **not** by itself make windows complete.
(4) Naming a *caller* who was never an assignee needs the FUB-user roster (Issue #19, §2.11);
the assignee is always nameable.

### 2.10 Single segment today — type/stage slicing is dead weight
Every active lead is `type='Buyer'`, `stage='Lead'`. No value in slicing by type/stage until FUB
actually uses more stages.

### 2.11 Only the assignee can be named
No global `userId → name` map (Issue #19); `processed_calls.source_user_id` has no companion name.
A board anchored on the **assigned** agent can label by name (`assignedTo`); naming an arbitrary
*other* caller cannot, until FUB-user ingestion lands.

## 3. Trust classification — which metrics to believe, when
| Metric family | Source | Trust |
|---|---|---|
| Uncontacted / contacted, lead source, assignment, owner-pool | **FUB snapshot flags** (`contacted`, `source`, `assigned*`) + `person.*` events | **Downtime-robust** — believe always |
| Called / connected / agent activity, timeline-correct attribution | `processed_calls` + `call.created` events | **Uptime-gated** — exact for any 24h/7d window inside continuous uptime (§2.1); complete going forward under continuous hosting |

**Build order (decided 2026-07-02):** the app runs continuously, so both families are trustworthy for
the forward 24h/7d windows v1 uses. Build the **lead-timeline layer** (§6) first, then the two reports
on top of it.

## 4. Top product findings (what the data says to act on)
1. **Facebook is the lead leak — and the owner pool is NOT the cause.** facebook 308 leads @ **51%**
   contacted vs Instagram 197 @ **92%** (the owner contacts his IG leads well — 97% owner-held, 92%
   contacted). ~150 uncontacted FB leads. Either FB quality is junk (stop paying for volume) or FB
   handling is broken (fix routing). Highest-value open question. *(Robust — snapshot-flag based.)*
2. **A "no lead missed" worklist exists today — 244 leads** `contacted=0`, ~210 older than 7 days.
   By agent: Mandeep 139 (107 >7d), Shahrukh 21, Gurbir 16, Navjot 16, Karanjot 12… *(Robust.)*
3. **`contacted` vs `called` divergence** (244 vs 113) — confirms the two are different signals (§2.5).

## 5. Operational context (for the ops dashboard, Phase 1)
- `workflow_runs`: 295 COMPLETED / 15 CANCELED / 6 FAILED.
- Tables with signal: `persons` 1,323 (725 ACTIVE LEAD, 598 ACTIVE UNKNOWN), `processed_calls` 4,131,
  `events` 2,367 (`call.created` 1,303 / `person.created` 774 / `person.state_changed` 290),
  `workflow_runs` 320. *(As of 2026-07-02 the `events` diary holds 1,555 `call.created` / 844
  `person.created` / 390 `person.state_changed`.)*

## 6. The lead-timeline layer — the reporting substrate
**Decided 2026-07-02.** The v1 reports are built on a reconstructed per-lead timeline derived from the
`events` diary (§2.9), **not** on ad-hoc joins to current state. Shape:

- **On-read SQL views over `events`** — no materialized table, no sync to keep consistent. Matches the
  "direct slice, no read-model yet" posture; RD-012's materialized read-model stays deferred to Phase 3.
  Materialize a specific view only if it is measurably slow.
- **Base view = holder-intervals.** Per lead, the sequence `(assignedUserId, valid_from, valid_to)`,
  reconstructed from `person.created` (the anchor) + the `assignedUserId` `person.state_changed` chain —
  a gaps-and-islands / window query. **This is the one non-trivial query;** everything above it is
  joins + `GROUP BY`.
- **Reports on top:**
  - **Report 1 — Source → leads → contacted (any channel).** Snapshot `source` + FUB `contacted` flag /
    `contacted`-flip events. Answers *"was this lead reached by anyone, any channel?"* — **coverage,
    not attribution**; no per-agent credit, so reassignment can't misattribute it.
  - **Report 2 — Assigned → contacted, timeline-correct.** Attribute each call/contact to the
    **holder-at-that-instant** via the holder-intervals view → true accountability, immune to
    reassignment. Contact defined as reached-by-any-channel (FUB `contacted`); the call log sharpens it
    with who-dialed-when.
- **Windows:** **24h / 7d**, forward-only (historical out of scope, §2.1).
- **Owner (`uid=1`) is a normal agent** — leads that sit with him now are shown as his (§2.2).
- **Coverage caveats to surface as counts, not hide:** orphan leads (called but absent from the
  `persons` mirror, §2.8) have no timeline to walk and fall out of Report 2; unlinked calls (§ scoping
  doc) are invisible. Both shrink going forward under continuous hosting.
