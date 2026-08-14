# Reporting Phase 2 — Accountability board (scoping / decision log)

> **Status: SCOPE SETTLED 2026-07-02 — timeline-first, two reports; ready to spec.** The remaining
> architectural forks (below) are decided; next step is `phase-2-implementation.md` (mirroring the
> Phase-1 split). **Headline change from the earlier draft:** the app now runs continuously, so the
> "accuracy gate" is no longer a build-blocker (§ demoted below); and the `events` diary turns out to
> already reconstruct a **full per-lead timeline**, so attribution is exact and the person-timeline is
> the **foundation** of this phase, not a deferred non-goal. v1 = a **lead-timeline layer** (on-read
> views over `events`) with **two reports** on top:
> 1. **Report 1 — Source → leads → contacted (any channel)** — coverage, snapshot-flag based.
> 2. **Report 2 — Assigned → contacted, timeline-correct** — accountability, attributing each contact
>    to the holder-at-the-time (immune to reassignment).
> Windows **24h / 7d**, forward-only (historical out of scope). Sibling context:
> [plan.md](./plan.md), [findings/data-model.md](./findings/data-model.md),
> [findings/data-truths.md](./findings/data-truths.md) (canonical definitions + data-quality truths —
> the semantic layer this board reads through),
> [findings/value-query-catalog.md](./findings/value-query-catalog.md) (the broader reporting-value
> map this slice sits inside), [architecture-direction.md](./architecture-direction.md),
> [RD-014](../../repo-decisions/RD-014-reporting-query-architecture.md) (deterministic-first /
> semantic-layer / NL-deferred decision this slice now sits under).

## Goal
Two reports over a shared **lead-timeline layer**: **(1)** where leads come from and whether they get
contacted (coverage), and **(2)** whether the **agent who held a lead when it was worked** contacted
it (accountability). Direct vertical slice — on-read views over existing tables, **no framework**
(RD-012 deferred to Phase 3), no new capture infra. The person-timeline that earlier drafts deferred is
now the **substrate** (it's reconstructable from data we already hold — see below), and a reporting
read-model *table* stays deferred (on-read views suffice; see Non-goals + Architecture posture).

## Accuracy: call coverage is the ceiling — reconcile job is MANDATORY (audit-corrected)
**History of this call, in order:** first framed as a build-blocker; then briefly demoted (2026-07-02) to
"healed by continuous hosting, reconcile optional"; then **that demotion was DISPROVEN the same day by a
live-API audit** (see Resolution below). Cross-checked the local DB against
the live FUB API: **when the app is running, local == FUB exactly — 0% variance** (window
2026-06-25 13:00–21:00 UTC, per-agent outbound/connected/distinct-lead rollup identical agent-for-agent,
e.g. Navjot 93/93 calls, 49/49 leads; all 7 exact). **Ingestion is correct** — no webhook filtering,
no mapping bug; the rollup SQL is sound. The only inaccuracy was **coverage**: pre-hosting the app was
not run 24/7, and webhooks fired during downtime were lost permanently (FUB delivers once, no retry).
Historical evidence: zero local calls 06-20→06-23 (FUB 142/91/225/193); best day 06-24 ~54%; all-time
pre-hosting local 4,120 vs FUB 129,156.

**Resolution (2026-07-02) — DISPROVEN the same day by a live-API audit; see the CORRECTION note
below.** The claim was: the app is hosted and runs continuously, so the event stream is complete going
forward, and v1 forward-only 24h/7d windows are trustworthy by construction, making a reconcile/backfill
job optional. **The 2026-07-02 audit refuted this:** recent call capture is **~41%**, the Report-2
red-list is **~40% false**, and the root cause is an **ephemeral Cloudflare quick-tunnel webhook ingress
+ FUB at-most-once delivery (no replay)** — not app uptime, not ingestion. Continuous hosting does not
make windows complete. **The reconcile/backfill job (now Phase 2c) is MANDATORY, not optional**, and a
**stable public webhook URL** is required alongside it. Timeline-correct attribution (§6) still fixes the
*naive-attribution* half of the old false-red; the *dropped-webhook* half is real, ongoing, and only the
reconcile job heals it. See [data-truths §2.1 CORRECTION](./findings/data-truths.md).

## The lead-timeline layer — the substrate (verified against live `events`, 2026-07-02)
The `events` table is an event-sourced diary that already captures, timestamped, everything that
happens to a lead — so we can reconstruct **who held a lead at any instant** and credit contacts
correctly. Verified live:
- **`call.created`** (1,555 rows) — `{userId, personId, createdAt, isIncoming, duration, outcome}`:
  who called which lead, when, direction, duration. `userId` = `assignedUserId` namespace → holder-vs-caller
  is an id comparison, not a guess.
- **`person.state_changed`** (390 rows) — `{changed_fields, previous, current}`: **reassignments**
  (`assignedUserId`/`assignedTo` before→after), **contacted flips** (`contacted` 0→1, 61 rows), source/
  tags/stage — each timestamped.
- **`person.created`** (844 rows) — anchors initial holder + source + contacted + tags.

**Base view = holder-intervals:** per lead `(assignedUserId, valid_from, valid_to)`, from `person.created`
+ the `assignedUserId` change-chain (a gaps-and-islands / window query — the one non-trivial piece).
Reports 1 & 2 are then joins + `GROUP BY` on top. **On-read SQL views, no materialized table**
(matches "direct slice, no read-model yet"; RD-012 read-model stays Phase 3). See
[data-truths §6](./findings/data-truths.md#6-the-lead-timeline-layer--the-reporting-substrate).

## Verified data ground truth (live DB, measured 2026-06-25)
Measured against the live `flux` DB, not assumed:

- **Denominator is healthy:** **694 active assigned leads across 11 agents**; 100% of active LEADs
  carry `assignedUserId`/`assignedTo`. The `person_details->>'assignedUserId'` join works.
- **Attribution *mechanics* are reliable — the null-rate "gate" is cleared.** `source_user_id` is
  null on 34.9% of `processed_calls`, **but perfectly correlated** — a row has `source_user_id`,
  `is_incoming`, AND `source_person_id` all present, or all null. On calls that link to a lead
  (have `source_person_id`), `source_user_id` is **0% null** (outbound and inbound). The nulls are
  **unmapped rows** (no lead/agent/direction) that can't join a lead anyway → invisible to the board.
  **Caveat (important): this measures only the calls we *hold*. The real accuracy ceiling is call
  *coverage*, not null-rate — see the [Accuracy section](#accuracy-completeness-is-uptime-bound-healed-by-continuous-hosting-not-a-blocker):
  local == FUB when the app is live, but downtime drops calls entirely.**
- **The ~1,382 unmapped/unlinked calls** (SKIPPED 1,172, FAILED 25, **TASK_CREATED 185**) are a
  **data-quality gap, not a per-agent accuracy problem** — surface as a count; likely backfillable
  from retained `raw_payload` (deferred). The 185 TASK_CREATED-but-unlinked rows warrant a look.
- **"Connected" must come from `duration_seconds`, not `outcome`.** Real `outcome` values are only
  blank (3,619), "No Answer" (330), "Bad Number" (6), "Left Message" (2) — **no positive value**.
  Outbound `duration_seconds`: 0 nulls, ~13% zero-duration, 689 ≥30s, avg-non-zero 71.7s. So
  **connected ≈ `duration_seconds >= 30`** (threshold tunable).
- **Call data spans 2026-04-17 → 2026-06-25**, so day-window presets (1/7/10/30d) are meaningful.

## Locked decisions (metric model)
Per lead in the assigned set, from `processed_calls` joined on `source_person_id`:

| Signal | Definition |
|---|---|
| **Assigned set (denominator)** | `persons` where `kind='LEAD' AND status='ACTIVE'` and `assignedUserId` present. Nothing else. |
| **Attempted** (primary) | ≥1 **outbound** call (`is_incoming=false`) by the **assigned agent** (`source_user_id = (assignedUserId)::bigint`) |
| **Connected** (stronger) | an Attempted call with **`duration_seconds >= 30`** (tunable) |
| **Lead-initiated** (separate signal) | ≥1 **inbound** call (`is_incoming=true`) from the lead — "the lead reached out", NOT credited as the agent calling |
| **Red** | active assigned lead with **no qualifying agent-outbound call** (within the active window) |

- **Group by `assignedUserId` (stable id); display `assignedTo` (name).** Grouping by display name
  splits/merges agents wrongly — observed live: "Arjun Singh Ahluwalia" (23) vs "Arjun Ahluwalia"
  (11) are split, almost certainly the same person / two FUB accounts.
- **Pure called-vs-not** — no assignment-date SLA (we have no reliable assigned-at; see Limitations).

## Time-window filter (decided)
- **Filter on call recency** via `processed_calls.call_started_at` (a reliable FUB timestamp) —
  **not** assignment date (unreliable). The board reads "of your assigned leads, how many called in
  the last N days." The **assigned-leads denominator stays current-state**; the window narrows the
  "called?" side. **Red = not called within the window.**
- **Presets (locked 2026-07-02): 24h / 7d only.** Forward-only; **all-time and historical presets are
  dropped** (pre-hosting data is out of scope, and short windows sit fully inside continuous uptime).
  Default **7d**; 24h is the leading-edge view.
- Endpoint param `?window=24h|7d`; the DTO echoes the active window.

## Data audit — categorized findings (verified against live DB + FUB API, 2026-06-25)
Fresh-eyes verification of the plan / SQL / filters, then **each finding chased to root cause**
(code read + read-only FUB API calls). **The SQL is mechanically sound** (attribution join valid;
`assignedUserId` and `source_user_id` are the same namespace) and the two scariest items **shrank
on investigation**. Numbers are live snapshots (DB grew during scoping — ~705 leads, 486 on owner).

### Category 1 — Board-correctness (handle IN the board)
- **Owner/default-assignee dominates — but it's legitimate FUB state, NOT a sync bug.**
  `assignedUserId=1` ("Mandeep Dhesi", FUB owner) holds **486 of ~705 active leads (68%)**.
  Verified via FUB API: sampled owner leads are genuinely `assignedUserId=1, claimed=true` in FUB
  *right now*, with FUB `created` dates in **2023** — old, long-claimed leads (our `created_at=2026-04-17`
  is just the backfill/first-seen day). **482/486 are >1h old.** So the 30-min release-to-pool
  automation isn't "broken" — these leads predate it / were already claimed; our mirror is accurate.
  Reassignment sync itself works (30 `assignedUserId`-change events; leads spread across 11 agents).
  **Display decision — settled 2026-07-02: render `uid=1` as a normal agent** (no segregation, no
  exclusion). Timeline-correct attribution (Report 2) removes the distortion segregation used to guard
  against. Not a defect either way.
- **Attribution-vs-coverage gap is almost entirely the owner pool — agents do NOT share phones.**
  Of leads called *only* by a non-assigned agent: **301 are owner-pool (uid=1)**, only **9** are
  real-agent leads called by someone else. For real agents attribution is **~99% clean**; the
  apparent gap was the Mandeep parking-lot. Board UI: *assigned agent* + *called by assigned? y/n*
  (+ optional *called-by*) — the called-by column rarely disagrees for real agents.
- **`source_person_id='0'`** (191 calls) is **FUB's sentinel for a person-less call**, stored
  verbatim — NOT a Flux default (code stores `null` when FUB sends null; `'0'` only appears when FUB
  sends `0`). They're overwhelmingly **SKIPPED inbound** calls from numbers not in FUB's address book
  (unknown callers). **Normalize `0→null` on write** + board treats `'0'` as unlinked.
- **Agent identity is ~1:1 — exceptions are tiny and FUB-side.** Only 2 ids: uid 31 ("Arjun
  Ahluwalia"/"Arjun Singh Ahluwalia" = a FUB name edit) and uid 30 (an ISA pond account also
  labelled "Mandeep Dhesi"). We snapshot `assignedTo` verbatim from FUB. **Group by `assignedUserId`,
  canonical name per id** — fully resolves it.

### Category 2 — System defects (own tickets, NOT board work)
- **`kind` classifier is broken (confirmed in code).** `mapStageToKind()` does a case-insensitive
  *exact* match (`"agent"→AGENT`); FUB sends `stage="Real Estate or Mortgage Agent"`, so all **540**
  agents fall to UNKNOWN (**0** rows are `kind=AGENT`). Doesn't corrupt the board (UNKNOWNs aren't
  active leads) but is a real bug. Fix: tokenize the stage (look for an `agent`/`lead` word); the
  **FUB onboarding-ingest pipeline** (existing leads + team members + designations → a real local
  user/agent roster) is the structural fix (also closes Issue #19).
- **`'0'` write-side defect — RULED OUT.** The only write path is `String.valueOf(FUB personId)`
  with `null→null`; we never synthesize `0`. The sentinel is FUB's (see Cat 1).

### Category 3 — Inherent completeness limits (caveat & accept; not board-fixable)
- **`persons` mirror is materially incomplete vs calls** — **1,543 of 2,622 (59%)** person-linked
  calls are **orphans** (reference a lead absent from the mirror). Webhook-delivery completeness.
  Surface as a caveat; optional future backfill.
- **~1,382 unmapped calls** (no person/agent/direction), incl. **185 `TASK_CREATED`** — a
  data-quality count; `raw_payload` backfill is a separate effort.

### Category 4 — Verified sound (no action)
- Attribution join valid; join-key namespaces match; `duration_seconds`-based "connected"
  discriminates (outcome free-text does not).

## Decided (2026-07-02) — was "Open"
- **Owner disposition → render `uid=1` as a normal agent.** No exclusion, no separate bucket; leads
  that sit with him now are shown as his. Report 2's timeline-correct attribution removes the false-red
  distortion the segregation used to guard against. (Supersedes the "exclude vs historical-book" fork.)
- **Sequencing → durable + call-derived both in v1**, on the shared timeline layer. The old "lead with
  snapshot flags, defer the call board until the reconcile job" split is moot: continuous hosting +
  the timeline make the attribution board buildable now. Report 1 (coverage) and Report 2 (attribution)
  ship together.
- **Windows → 24h / 7d, default 7d, forward-only.** (See Time-window filter.)
- **Cat 2 kind classifier / Cat 3 completeness** stay separate tickets / documented caveats;
  `'0'`-sentinel and identity are resolved (normalize-on-write; group-by-id).

## Still open (before/at spec time)
- **Connected threshold** — `duration_seconds >= 30`? (tunable; ground-truth supports it). Only matters
  if Report 2 grades on *connected* vs *attempted*; v1 "contacted" = FUB flag (any channel), so this is
  a secondary signal.
- **Per-agent drill** to the actual leads (name + phone) — in v1, or counts-only first? (PII masking if
  shared.)
- **Board UI** — new screen; reuse Phase-1 primitives, or get a design handoff (Phase 1 had one)?
- **Report 1 "contacted" definition** — confirmed = FUB `contacted` flag (multi-channel, "reached by
  anyone"), **not** attribution. Locked; noted here so it isn't re-litigated.

## Actionable items (data-detective pass, 2026-06-25)
Prioritized by impact × ease. Re-measured against live DB this date; corroborates the audit above
and sharpens three numbers. Tags: **[BOARD]** = build into Phase-2 board, **[DECISION]** = needs an
owner call before build, **[TICKET]** = separate defect/effort, **[CAVEAT]** = document & accept.

**P0 — RESOLVED 2026-07-02 (were blockers; both dissolved)**

- [ ] **[TICKET] Close the ingestion gap — REOPENED 2026-07-02 (audit); NOT resolved by hosting.** The
  "resolved by continuous hosting / reconcile optional" call was **wrong**: a live-API audit found ~41%
  recent call capture and ~40% false reds *in the hosted window*. Root cause = **ephemeral Cloudflare
  quick-tunnel webhook ingress + FUB at-most-once delivery (no replay)**, not uptime. **Mandatory fix:**
  a **stable public webhook URL** + a scheduled FUB `/v1/calls`+`/v1/people` since-last-sync
  reconcile/backfill (**Phase 2c, on the critical path for R2**). See data-truths §2.1 CORRECTION.
- [x] ~~**[DECISION] Dispose of the owner bucket.**~~ **Resolved: render `uid=1` as a normal agent.**
  The naive "~70% of all leads RED, mostly false" failure mode came from *naive current-holder
  attribution*, which **Report 2's timeline-correct attribution eliminates** (credit goes to the
  holder-at-the-time). So no exclusion and no separate bucket are needed — Mandeep is graded like any
  agent on the leads he currently holds. (Owner still holds ~68%: 489/715, ongoing 80%→52%→56% Apr→Jun —
  legitimate FUB state, not a bug.)
- [ ] **[BOARD] Grade real-agent leads as the headline; the metric is sound and the number is good.**
  Honest segment split (re-measured 2026-06-25):

  | Segment | Active leads | Called by anyone | Called by **assigned agent** |
  |---|---|---|---|
  | Assigned to **real agent** | 226 | **74.3%** | **70.4%** |
  | Assigned to **owner `uid=1`** | 489 | 73.2% | 11.2% |

  On real-agent leads, **159 of 168 called were called by the assigned agent (95%)** → attribution is
  trustworthy and the gap is the owner pool, not phone-sharing. Ship 70.4% / 74.3% as the defensible
  headline once `uid=1` is segregated (P0 above).

**P1 — build into the board**

- [ ] **[BOARD] Group by `assignedUserId`, render canonical name per id.** Prevents the Arjun
  (uid 31) name-edit and uid-30 ISA-pond splits/merges. (Audit Cat 1/4.)
- [ ] **[BOARD] Normalize `source_person_id '0' → null` on write; board treats `'0'`/null as unlinked.**
  191 calls; FUB's person-less sentinel, not a Flux default. (Audit Cat 1.)
- [ ] **[BOARD] "Connected" = `duration_seconds >= 30` (tunable), never `outcome`.** Outcome free-text
  has no positive value (91% blank). (Ground truth + Cat 4.)
- [ ] **[DECISION] Confirm defaults:** call-recency window **30d**? connected threshold **30s**?
  per-agent **drill to the red leads** in v1, or counts-only?

**P2 — separate tickets / accepted caveats (do NOT block Phase 2)**

- [ ] **[TICKET] Fix `mapStageToKind()` exact-match bug** — 540 agents fall to `kind=UNKNOWN`
  (0 rows `kind=AGENT`). Tokenize the stage word. Pairs with the FUB onboarding-ingest pipeline
  (closes Issue #19). Doesn't corrupt the board. (Audit Cat 2.)
- [ ] **[CAVEAT] Mirror incompleteness:** 59% of person-linked calls are orphans (lead absent from
  mirror); ~1,382 unmapped calls (incl. 185 `TASK_CREATED`) are invisible to the board. Surface both
  as counts; `raw_payload` backfill is a separate effort. (Audit Cat 3.)
- [ ] **[CAVEAT] No reliable assigned-at** → board is "called vs not", not "called on time". Person-timeline
  feature is the eventual fix. (Known limitations.)

## Architecture posture (settled 2026-07-02)
- **Timeline-first, on-read.** A base **holder-intervals view** over `events` (per lead
  `(assignedUserId, valid_from, valid_to)` from `person.created` + the `assignedUserId` change-chain),
  then Report 1 and Report 2 as views/queries joining on top. **On-read SQL views, no materialized
  table** (RD-012 read-model stays Phase 3).
- Placement: `controller/ReportingController` (or per-report controllers) + dashboard-local read repos
  (`…ReadRepository` + JDBC adapters) + DTOs, under `service/reporting/…`.
  `@Transactional(readOnly, REPEATABLE_READ)`.
- **Schema changes:** a Flyway **expression index** on `persons ((person_details->>'assignedUserId'))
  WHERE kind='LEAD'`; consider an index supporting the `events` timeline walk
  (`entity_type, entity_id, created_at` already exists — reuse it) and on `call.created` lookups.
- FE: port/adapter/contract + new route(s), reusing Phase-1 patterns.
- BE-first order: **holder-intervals view + Testcontainers IT** (proves the gaps-and-islands window SQL
  + JSONB extraction + `::bigint` cast on real Postgres) → Report 1 read path → Report 2 read path →
  service + DTO + controller → FE.

## Non-goals / deferred
- ~~**Person timeline**~~ — **no longer deferred; it is the substrate of this phase** (holder-intervals
  view over `events`). See "The lead-timeline layer" above.
- **Reporting read-model *table*** (denormalized/materialized mirror) — deferred (the RD-012 read model,
  Phase 3); v1 is on-read **views** + the one index. (The timeline is *reconstructed on read*, not
  materialized.)
- **FUB-user ingestion** (Issue #19) — naming arbitrary (non-assignee) call-makers; not needed, the
  reports anchor on the assignee whose name is free.
- **SLA / time-to-first-call** as a graded metric (we have first-seen, not true FUB intake),
  task/appointment outcomes, conversion, the framework (Phase 3).

## Known limitations
- **Assigned-at is reliable for post-ingestion changes, not the pre-ingestion baseline.** Every
  reassignment observed while ingesting is timestamped in `events`, so the holder-intervals timeline is
  exact going forward; but `person.created` = first-seen-by-us (not true FUB intake), so *time-to-first-
  call* remains directional, not SLA-grade. The board is "contacted vs not (attributed correctly)," not
  yet "contacted on time."
- **Board names only the assignee** — a call made by someone other than the assigned agent can't be
  named (no userId→name map). Fine for an assignee-anchored board.
- **Unlinked calls** (~1,382) are invisible to the board (no lead linkage); surfaced as a count.
