# Reporting Platform — Plan

> Plan the whole reporting **layer**; build the **MVP** (+ dashboard metrics) first.
> The MVP and the dashboard are the first two reference modules that prove the platform;
> heavy future work (FUB mirror, Vanna text-to-SQL, task-completion) plugs in through
> seams defined now but not filled.
>
> Architecture of record: [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md).
> Data ground truth: [findings/data-model.md](./findings/data-model.md).
> Direction & rejected paths: [architecture-direction.md](./architecture-direction.md).

## Goal

A reporting platform where **adding a new report is a provider + DTO, not a project**,
and where the first build delivers the accountability MVP and the redesigned dashboard's
metrics over data we already hold — no new capture infra.

**MVP (the thing we build now):** track that every assigned lead gets called. If a lead
is assigned to an agent and the agent hasn't called it → show it **red**. The sincerity /
accountability board.

## Scope

### In (this pass)
- **Foundation:** research/findings consolidated, RD-012, this plan + phase tracker, the
  "how to add a report" recipe (in RD-012).
- **Platform skeleton:** `ReportProvider`, `ReportRegistry` (auto-discovery), the
  `ReportingQuery` port + deterministic-SQL adapter, the shared `definitions` module, the
  generic reporting controller.
- **Module — Accountability MVP:** assigned → called, red/green, grouped by agent.
- **Module — Dashboard metrics:** the existing-data capabilities the redesigned dashboard
  needs (see [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)).

### Out (named, deferred — slot in later via the seams)
- The **FUB CDC mirror** and anything needing it: task creation/completion (Q5/Q6),
  "useful" outcome (Q4), deeper funnel stages (appointments/deals).
- **Vanna text-to-SQL** ad-hoc questions and its security RD.
- **FUB-user ingestion** (Issue #19) — naming arbitrary call-makers. Not needed; the MVP
  anchors on the assigned agent, whose name is already captured (`assignedTo`).
- **Text/SMS contact**, true FUB intake date, on-time task semantics.

## Design

Per RD-012: providers (auto-discovered) → generic controller; providers read only through
the `ReportingQuery` port (deterministic SQL now, mirror-backed read model later); metric
semantics centralized in `definitions`.

```
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
Phase 0 (docs) → Phase 1 (skeleton; one trivial provider end-to-end proves the seam) →
Phase 2 (accountability MVP; contains the null-rate gate) → Phase 3 (dashboard metrics;
reuses Phase-1 foundation + Phase-2 definitions). Phases 2 and 3 both depend only on
Phase 1, not on each other.

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
- **Over-abstraction of the skeleton** — building registry/port for only two modules could
  gild. *Detect:* Phase 1 must carry a real second consumer (dashboard) to justify the
  seam; if it doesn't earn its keep, collapse to direct queries.

## Validation criteria
- Phase 1: a trivial provider is reachable at `/api/reporting/...` end-to-end; adding it
  required only a provider + DTO.
- Phase 2: red/green board renders per agent; the null-rate gate result is recorded; an
  agent with a known call shows green (no false red on attributable calls).
- Phase 3: the redesigned dashboard's existing-data panels are backed by real endpoints;
  the funnel matches the accountability numbers (shared definitions, no divergence).

## Linked decisions
- [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) — this
  architecture (Proposed; ratify on Phase 1).
- Future RD — text-to-SQL security posture, created when Vanna is picked up.

## Decisions
- Architecture, provider/port model, generic-controller-by-default, deferred mirror/Vanna:
  **RD-012**.
- Accountability "called" = any outbound call (attempt counts); coverage vs. attribution
  layering: this plan, owner-approved 2026-06-19.
- Open (resolve at Phase 1): `ReportingQuery` method granularity — see RD-012 Consequences.
