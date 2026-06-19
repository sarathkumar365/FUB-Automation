# Reporting Platform — Plan

> Plan the whole reporting **layer**; build it as **direct vertical slices first, extract
> the framework last.** The accountability MVP and the dashboard are two plain, un-abstracted
> reports; the platform framework (RD-012) is then *extracted from* those two working
> examples (rule of three) rather than committed up front. Heavy future work (FUB mirror,
> Vanna text-to-SQL, task-completion) plugs in through the framework once it exists.
>
> Architecture **target — to extract, not build-first**: [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) (Proposed).
> Data ground truth: [findings/data-model.md](./findings/data-model.md).
> Direction, rejected paths & known limits: [architecture-direction.md](./architecture-direction.md).

## Goal

A reporting platform where **adding a new report is a provider + DTO, not a project**,
and where the first build delivers the accountability MVP and the redesigned dashboard's
metrics over data we already hold — no new capture infra.

**The accountability board (the headline report):** track that every assigned lead gets
called. If a lead is assigned to an agent and the agent hasn't called it → show it **red**.
The sincerity / accountability board. *(Build order: dashboard metrics are built first by
owner preference; this board is the second slice — see "Order of work".)*

## Scope

### In (this pass)
- **Foundation:** research/findings consolidated, RD-012 (Proposed), this plan + phase
  tracker, the "how to add a report" recipe (in RD-012).
- **Dashboard metrics (direct slice — built first):** the existing-data capabilities the
  redesigned dashboard needs (see [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)).
  One controller, direct SQL, a DTO — **no framework.**
- **Accountability MVP (direct slice):** assigned → called, red/green, grouped by agent.
  Second plain slice, same style.
- **Extracted framework:** `ReportProvider`, `ReportRegistry` (auto-discovery), the
  `ReportingQuery` port + deterministic-SQL adapter, the shared `definitions` module, the
  generic reporting controller — **extracted from the two slices above, then RD-012 ratified.**

### Out (named, deferred — slot in later via the seams)
- The **FUB CDC mirror** and anything needing it: task creation/completion (Q5/Q6),
  "useful" outcome (Q4), deeper funnel stages (appointments/deals).
- **Vanna text-to-SQL** ad-hoc questions and its security RD.
- **FUB-user ingestion** (Issue #19) — naming arbitrary call-makers. Not needed; the MVP
  anchors on the assigned agent, whose name is already captured (`assignedTo`).
- **Text/SMS contact**, true FUB intake date, on-time task semantics.

## Design

**Build order, not the end state.** Phases 1–2 are plain vertical slices — each its own
controller + direct SQL + DTO, no shared abstraction. Phase 3 *extracts* the framework
below **from** those two working slices; the diagram is where we expect to land, not what
Phase 1 ships. RD-012 is the **target**, ratified only once it's been extracted and the two
slices sit on it unchanged.

```
TARGET (extracted in Phase 3 — not Phase 1):
controller/reporting/ReportingController         GET /api/reporting/{module}/{report}
        │ routes via
service/reporting/shared/ReportRegistry  ──discovers──▶ ReportProvider beans
service/reporting/shared/query/ReportingQuery (port)
        └─ deterministicSqlAdapter  ──▶ persons · processed_calls · events · workflow_run*
                                        (later: mirror-backed read model — same port)
service/reporting/shared/definitions/  Called · Contacted · Assigned · Lead
service/reporting/modules/accountability/   AccountabilityProvider + DTO
service/reporting/modules/dashboard/        DashboardProvider(s) + DTO
```

Why this order: the right shape of the `ReportingQuery` port and the `definitions` module
is unknowable from zero reports. Two real, *different* slices (a per-agent worklist and a
set of dashboard aggregates) expose what actually needs to be shared — so we extract from
evidence instead of guessing the contract up front. See "Order of work".

### Accountability MVP — semantics
- **Universe:** `persons` where `kind=LEAD` and `assignedUserId` is set.
- **Called:** an outbound (`is_incoming=false`) `processed_calls` row for the lead.
  Attempt counts (voicemail/no-answer = effort); outcome/connected ignored in v1.
- **Two layers, degrading on data quality** (decided by the Phase-2 null-rate gate):
  - **Coverage** ("no lead missed") — called by *anyone*; depends only on
    `source_person_id`. Always ships.
  - **Attribution** ("did *their* agent call") — `source_user_id == assignedUserId`;
    gated on the `source_user_id` null rate. Off if the rate is too high.
- **Display:** group by `assignedUserId`, labelled with `assignedTo`. Red = assigned, not
  called.

### Dashboard metrics — what fits existing data
Buildable now: windowed counts, hourly time-series, period deltas, the funnel **leads →
assigned → called** (which IS the accountability funnel — shared foundation), the
open-failures worklist, recent runs, live readout. Deeper funnel stages (appointments/
deals) are deferred to the mirror and slot in as a later provider.

## Order of work & dependencies
Phase 0 (docs) → **Phase 1 (dashboard metrics — direct slice)** → **Phase 2 (accountability
MVP — direct slice; contains the null-rate gate)** → **Phase 3 (extract the framework from
Phases 1–2 + ratify RD-012).** Phases 1 and 2 are independent of each other (dashboard goes
first by owner preference, not dependency); Phase 3 depends on both existing.

This is the **rule of three**: don't abstract until you have ≥2 real, *different*
implementations to abstract *from*. The accountability worklist and the dashboard
aggregates are deliberately different shapes — extracting a framework that serves both is
how we know the abstraction is right, instead of locking in RD-012's contract blind. Phase
3 is a pure refactor: the two slices must keep behaving identically once moved onto the
extracted framework.

## Non-goals (and why)
- **No mirror / no Vanna in this pass** — the MVP and dashboard metrics are answerable
  from existing tables; building capture infra now would delay the value and isn't needed.
- **No FUB-user ingestion** — assigned-agent name is already captured.
- **No on-time task semantics** — deprioritized by owner; historical "was it ever done"
  only, and that's mirror-era anyway.

## Risks (with mid-flight detection)
- **`source_user_id` null rate too high** → attribution unreliable. *Detect:* Phase-2
  gate measures it before wiring attribution. *Fallback:* ship coverage-only; fix the
  parser + backfill from retained `raw_payload`.
- **`outcome`/string drift** — not a v1 risk (outcome unused in the MVP); becomes relevant
  only when connected-vs-attempt is introduced.
- **`person.created` ≠ intake** — affects "leads in today" counts on the dashboard. *Detect:*
  spot-check against FUB `created`. *Mitigation:* label as first-seen, or snapshot FUB
  `created` later.
- **Over-abstraction of the framework** — committing the registry/port/definitions contract
  before it's earned. *Mitigation (this plan's core sequencing):* the framework is
  **extracted in Phase 3 from two working slices**, never built first. *Detect:* if Phase 3
  finds the two slices share almost nothing worth abstracting, that's the signal to keep
  them as direct slices and shrink or drop RD-012 rather than force the seam.
- **The `ReportingQuery` "swap the backing store, providers don't change" promise may leak**
  for analytical SQL (a provider's SQL encodes schema knowledge). *Detect:* Phase 3
  extraction reveals whether the port is a genuine abstraction or a thin pass-through;
  resolve the method-granularity question then, with two real call-sites in hand.

## Validation criteria
- Phase 1: the redesigned dashboard's existing-data panels are backed by real endpoints.
  Plain slice — no framework yet. Establishes the shared "called"/"assigned" logic the
  accountability slice will reuse.
- Phase 2: red/green board renders per agent; the null-rate gate result is recorded; an
  agent with a known call shows green (no false red on attributable calls). Reuses Phase-1's
  funnel logic (same "called"/"assigned", no divergence). Second plain slice.
- Phase 3: the framework is extracted; **both slices behave identically after moving onto
  it** (pure refactor, tests unchanged); adding a *third* hypothetical report would now be a
  provider + DTO. RD-012 ratified → Accepted.

## Linked decisions
- [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) — the framework
  architecture (Proposed; **extracted and ratified in Phase 3**, not built first).
- Future RD — text-to-SQL security posture, created when Vanna is picked up.

## Decisions
- **Sequencing — extract, don't pre-build (owner-approved 2026-06-19 stress-test):** build
  the dashboard and accountability MVP as direct vertical slices first; extract RD-012's
  framework from them on the rule of three. RD-012 stays Proposed until Phase 3. **Build
  order: dashboard first, accountability second (owner preference 2026-06-19).**
- Architecture target, provider/port model, generic-controller-by-default, deferred
  mirror/Vanna: **RD-012**.
- Accountability "called" = any outbound call (attempt counts); coverage vs. attribution
  layering: this plan, owner-approved 2026-06-19.
- Open (resolve at Phase 3, with two call-sites in hand): `ReportingQuery` method
  granularity — see RD-012 Consequences.

## Known limits & open questions
Surfaced by the 2026-06-19 stress-test; tracked in
[architecture-direction.md](./architecture-direction.md) "Known limits": the data ceiling
(only calls/persons/notes ingested), multi-tenancy scoping, push/alerting being out of the
pull-only architecture, no caching/materialization strategy, and definitions likely needing
parameterization. None block the MVP; all are deferred deliberately.
