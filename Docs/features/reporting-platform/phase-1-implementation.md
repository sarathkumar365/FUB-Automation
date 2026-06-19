# Reporting Phase 1 — Operations Dashboard (implementation)

> **Status: implementation plan — not yet built.** A **direct vertical slice**: one
> controller + a snapshot service + three plain aggregators + a DTO. **No framework**
> (`ReportProvider`/`ReportingQuery`/registry are deferred to Phase 3 per
> [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md)). These
> aggregators are dashboard-local and must NOT be generalised here.
>
> Design: `ui/Automation Engine Design System/design_handoff_dashboard/` (Direction A).
> Data sources verified in [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
> "Verified backend ground truth". Plan: [plan.md](./plan.md) (Phase 1).

## Goal

Replace the redesigned operations dashboard's data layer (today: 4 client-side list calls
stitched with `systemHealth.mode='placeholder'`) with **one server-computed snapshot** so
every widget shows numbers that agree by construction. Operational pipeline health only —
not lead accountability (that's Phase 2).

## Why one snapshot (the core constraint)

The design demands internal consistency: replaying a failure must lower the hero "Open
failures", the funnel "Failed" stage, and the fail-rate % **together**. The handoff is
explicit — "wire these to the same snapshot numbers." So we compute each number **once**,
server-side, grouped by **data source**, and return one `DashboardSnapshotDto`. The UI binds
to that single object; widgets cannot diverge.

## Architecture (the slice)

```
GET /admin/dashboard/snapshot?window=24h
        │
DashboardSnapshotService.snapshot(window)         ← computes ONE window + "now" anchor, passes to all
   ├─ RunMetricsAggregator(window)        → workflow_runs
   ├─ PipelineMetricsAggregator(window)   → webhook_events + events
   └─ CallMetricsAggregator(window)       → processed_calls
        │ assembles
   DashboardSnapshotDto   ──▶  UI binds every widget to this one object
```

- Three aggregators, one per **data source** (not per widget — the funnel rail alone draws
  from two of them). Each owns its SQL; each metric computed once.
- One shared `window` + `now` computed at the top and passed down, so funnel conversion
  ratios reconcile (Ingested and Runs counted over the identical window).
- Plain `@Service`/`@Component` Spring beans, dashboard-local. **No port, no registry, no
  generic interface** — that's the Phase-3 extraction, done from evidence later.

## The endpoint

- `GET /admin/dashboard/snapshot` — **no query params in v1** (`window` fixed at 24h; see
  Contract edge cases). Returns the full `DashboardSnapshotDto` in one response. Admin-auth,
  same as the other `/admin/*` endpoints.
- New `DashboardController` (controller layer). May be re-homed under the reporting
  namespace when the framework is extracted (Phase 3) — fine, it's one endpoint.
- UI: `useDashboardSnapshotQuery` collapses from 4 calls to **1**. **No polling** — see
  "Liveness & refresh" below.

## `DashboardSnapshotDto` (the shape)

```
DashboardSnapshotDto {
  window:        { from, to, label }                          // the anchor, echoed back
  hero: {
    state:        "HEALTHY" | "ALL_CLEAR"                      // ALL_CLEAR when openFailures == 0
    openFailures: int                                          // = runs + calls open failures (see window below)
    stats: {
      runs:        { value: int,   delta: Delta }              // runs in window (excl. DUPLICATE_IGNORED)
      successRate: { value: float, delta: Delta }              // COMPLETED / (COMPLETED + FAILED)
      openFailures:{ value: int,   delta: Delta }
    }
  }
  throughput: {
    series:  int[24]            // hourly ingested (webhook_events) over window
    peak:    int
    avg:     int
    perMin:  int                // events in the last 60s — the top-right readout
  }
  funnel: {
    ingested:     Stage         // latest-hour webhook_events count + 8-pt spark
    domainEvents: Stage         // latest-hour events count       + 8-pt spark
    runs:         Stage         // latest-hour workflow_runs count + 8-pt spark
    failed:       Stage         // openFailures count             + 8-pt spark
    conversions:  { normalizedPct, toRunsPct, failRatePct }
  }
  recentRuns:     RunRow[]      // top N by created_at desc
  needsAttention: FailureRow[]  // FAILED runs + FAILED calls, newest first
}

Delta       { value: float, direction: "UP" | "DOWN" | "FLAT" }   // vs. prior equal window
Stage       { value: int, spark: int[8] }
RunRow      { id, workflowKey, status, startedAt, completedAt, durationSec }
FailureRow  { kind: "RUN" | "CALL", ref, workflowKey, reason, retries: int|null, ageSeconds }
```

## Aggregators & queries (illustrative SQL)

### RunMetricsAggregator — `workflow_runs`
- **Status breakdown (window):** `SELECT status, COUNT(*) … WHERE created_at >= :from GROUP BY status`.
  Yields `runs` (sum excl. `DUPLICATE_IGNORED`), `successRate = COMPLETED/(COMPLETED+FAILED)`.
- **Deltas:** same query over the prior equal window `[from - len, from)`; compute % / point deltas.
- **Hourly series (funnel runs stage + spark):** `GROUP BY date_trunc('hour', created_at)`.
- **Recent runs:** reuse the existing `WorkflowRunSummary` query (`/admin/workflow-runs`,
  sorted `created_at DESC`). Duration = `updated_at − created_at`; completed = `updated_at`.
- **Failed runs (worklist):** `WHERE status='FAILED' AND created_at >= :failuresFrom` →
  `{id, workflow_key, reason_code, created_at}`. **Runs have `reason_code`, no `retry_count`**
  → `retries = null` for run rows.

### PipelineMetricsAggregator — `webhook_events` + `events`
- **Ingested (funnel + throughput):** hourly `GROUP BY date_trunc('hour', received_at)` on
  `webhook_events`; latest hour = funnel value, 24 buckets = throughput series.
- **Domain events (funnel):** hourly on `events.created_at`; latest hour = funnel value.
- **perMin readout:** `COUNT(*) … WHERE received_at >= now() - interval '60 seconds'`.
- **Conversions:** `normalizedPct = events/ingested`, `toRunsPct = runs/events` (runs latest
  hour from the run aggregator's series — passed in, not re-queried).

### CallMetricsAggregator — `processed_calls`
- **Failed calls (worklist):** `WHERE status='FAILED' AND updated_at >= :failuresFrom` →
  `{call_id, failure_reason, retry_count, updated_at}` → FailureRow kind=CALL.

`needsAttention` = run failures ∪ call failures, sorted by age. Run rows: Replay is
**read-only** (navigates to run detail). Call rows: wired to existing
`POST /admin/processed-calls/{id}/replay`; on success, invalidate the snapshot query.

## Definitions & decisions (locked here)

- **Success rate = `COMPLETED / (COMPLETED + FAILED)`.** Confirmed by the design's own
  numbers: `289 / (289 + 9) = 97.0%`. Excludes PENDING/CANCELED/BLOCKED/DUPLICATE_IGNORED
  from the denominator.
- **`runs` total excludes `DUPLICATE_IGNORED`** (not a real execution). Design total
  `312 = 289+9+4+10` (COMPLETED+FAILED+PENDING+CANCELED) confirms.
- **Status → badge mapping:** `COMPLETED→Succeeded` (ok), `FAILED→Failed` (error),
  `PENDING→Running` (info), `CANCELED→Canceled` (muted), `BLOCKED→Blocked` (warning),
  `DUPLICATE_IGNORED→` muted "Duplicate" badge if it surfaces in recent runs; excluded from
  counts/rates.
- **Funnel stage values = latest complete hour** (a rate, "events/hr"); hero stats =
  full-window totals. Sparks = last 8 hourly buckets per stage.
- **Two windows:** `window` (default 24h) drives hero stats / throughput / funnel; a
  separate **`failuresFrom` (default last 7d)** bounds the open-failures count + worklist,
  since runs have no "resolved" flag and all-time FAILED would be unbounded. `openFailures`
  in hero and funnel both read this same bounded set.
- **Run-replay: read-only in v1.** Runs have no replay path; a naive re-submit would
  `DUPLICATE_IGNORED` (idempotency key `WorkflowKey|Source|SourcePersonId|EventId`), and a
  real re-run risks double side effects (partial-success runs). Deferred — see Non-goals.

## Contract edge cases (locked)

Pinned so UI and BE agree before integration:

- **Zero denominators → `null`** (the UI renders `—`, never `0`/`NaN`):
  - `successRate.value` when `COMPLETED + FAILED == 0` (no terminal runs) → `null`.
  - any `Delta` whose prior-window baseline is `0` → `{ value: null, direction: "FLAT" }`.
  - `conversions.*` (`normalizedPct`, `toRunsPct`, `failRatePct`) when the denominator is
    `0` → `null`.
- **List sizes:**
  - `recentRuns` = **5** (fixed).
  - `needsAttention` capped at **50**, newest first. `hero.openFailures` stays the *true*
    count (may exceed 50); the UI shows "+N more" from `openFailures − needsAttention.length`.
- **`window` is fixed 24h in v1** — not a client-facing param. The server always returns 24
  hourly points; the design's 12h/24h throughput toggle is **client-side slicing** of those
  points, not a refetch. `window` stays in the DTO as the echoed `{ from, to, label }` anchor.
- **Serialization:**
  - all timestamps are **ISO-8601 UTC strings**.
  - `ageSeconds` and `durationSec` are **server-computed ints** — the UI never does clock
    math (so "10m ago" can't skew on client clock drift).
  - percentages are numbers (e.g. `97.0`), not pre-formatted strings.
- **Errors** inherit the existing `/admin/*` error envelope — no bespoke error shape here.

## Frontend

- Collapse `useDashboardSnapshotQuery` to one call — **no `refetchInterval`** (event-driven;
  see "Liveness & refresh").
- Expand the `DashboardSnapshot` TS type to mirror the DTO (Zod schema per repo convention).
- Two new SVG chart components: **AreaChart** (throughput) and **Sparkbars** (funnel) —
  thin, token-driven, no chart lib (per handoff).
- Recreate hero / funnel rail / recent-runs / needs-attention per the handoff, reusing
  existing `shared/ui/*` (Button, Badge, DataTable, ConfirmDialog, toast). Pulse dot = CSS.
- A small **"updated {time}"** label (from `window.to`) so the point-in-time figures read
  honestly under the always-pulsing dot.

## Liveness & refresh (v1)

**v1 does not poll.** The snapshot is refreshed by **events, not a timer**:

- **on mount** — fetched when the dashboard opens;
- **on window focus** — `refetchOnWindowFocus` (TanStack default, kept on) refetches when the
  operator tabs back — exactly the "they check, then leave" usage, fresh whenever they look;
- **on replay** — a successful call-replay invalidates the snapshot so counts drop.

No `refetchInterval`. The **"live" feeling is the CSS pulse dot** (continuous, no data behind
it), not the number's refresh rate. `events/min` (`throughput.perMin`) is a plain field in
the snapshot, frozen at fetch time and refreshed on the same events — honest because of the
"updated {time}" label.

**`events/min` stays a field in the snapshot — not a separate endpoint.** It shares the
snapshot's exact access pattern (same triggers, same cadence, same consumer), so it belongs
in the one endpoint. A dedicated `/pulse` endpoint would, in the no-poll model, either return
a value already in the snapshot (redundant) or be polled faster (the polling we rejected).

**Going live later is a deferred, isolated change — no machinery built now.** The
single-snapshot-object boundary behind one hook *is* the switch:
- time-based liveness → add `refetchInterval` (one line);
- true push → swap the hook's source to an SSE subscription emitting snapshots (UI unchanged;
  the codebase already has SSE infra via `webhookStreamPort`);
- independent fast pulse → *then* promote `perMin` to its own `GET /admin/dashboard/pulse`
  and point a small fast hook at it (cheap precisely because it's already a discrete field).

The trigger to do any of these is an **access-pattern divergence** (the pulse needing a
different cadence than the snapshot) — which does not exist in v1.

## Order of work (sub-steps within Phase 1)

1. **Backend snapshot endpoint** — DTO + `DashboardSnapshotService` + 3 aggregators + queries.
   Done signal: `GET /admin/dashboard/snapshot` returns coherent real numbers; a unit/IT
   test asserts hero.openFailures == funnel.failed.value (the consistency invariant).
2. **Frontend data layer** — one query, expanded type/Zod, snapshot binding.
3. **Frontend presentation** — hero, funnel, recent runs, needs-attention; AreaChart +
   Sparkbars; call-replay wired, run rows read-only.

## Non-goals (deferred)

- **Run-replay + a run "resolved/handled" flag** — net-new, semantically hard (step-level
  resume / idempotent side effects). Its own future mini-project.
- The framework (`ReportProvider` etc.) — Phase 3.
- Lead/accountability metrics — Phase 2.
- Any time-based polling / live `events/min` — v1 is event-driven only (mount / focus /
  replay). Polling, SSE, or a `/pulse` endpoint are deferred, isolated swaps (see "Liveness
  & refresh").
- Pre-computed/materialized aggregates — on-read is fine at this scale.

## Risks (with detection)

- **Unbounded open-failures** if `failuresFrom` is omitted — runs never auto-resolve.
  *Detect:* worklist length / count sanity check. *Mitigation:* the 7d bound above.
- **Funnel ratios not reconciling** if aggregators use different windows. *Detect:* the
  consistency test in step 1. *Mitigation:* single shared window/anchor.
- **Aggregators drifting toward the framework.** *Detect:* a generic interface or
  cross-slice reuse appearing. *Mitigation:* keep them concrete + dashboard-local; the
  accountability slice uses different tables anyway.
- **Package rename in flight** (`com.fuba.automation_engine → com.flux` on a parallel
  branch). *Mitigation:* land Phase 1 backend in whatever package is current at build time;
  sequence the reporting branch relative to the rename before starting backend code.

## Validation criteria

- `GET /admin/dashboard/snapshot` returns one coherent payload; **hero.openFailures ==
  funnel.failed.value** and the conversion %s reconcile against the stage values (invariant
  test).
- Success rate matches `COMPLETED/(COMPLETED+FAILED)` on a seeded fixture.
- Replaying a failed **call** lowers openFailures across hero + funnel after refetch;
  a failed **run** row is read-only (navigates, no fake action).
- Dashboard renders the handoff design with real data; `events/min` shows a real value;
  the snapshot refreshes on mount / window-focus / replay (no timer), with an "updated
  {time}" label and a continuous CSS pulse dot.

## Linked

- [plan.md](./plan.md) (Phase 1) · [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
  · [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) (framework deferred to Phase 3).
