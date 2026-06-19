# Reporting Phase 1 — Operations Dashboard (implementation)

> **Status: implementation plan — not yet built.** A **direct vertical slice**: one
> controller + a snapshot service + a dashboard-local JDBC read repository + a DTO. **No
> framework** (`ReportProvider`/`ReportingQuery`/registry are deferred to Phase 3 per
> [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md)). The read repo is
> dashboard-local and must NOT be generalised here.
>
> Design: `ui/Flux Design System/design_handoff_dashboard/` (Direction A).
> Data sources verified in [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
> "Verified backend ground truth". Plan: [plan.md](./plan.md) (Phase 1).

## Changelog

- **2026-06-19 (rev 2) — backend design finalised (this is the build-ready spec).** Decisions
  taken in a design session, all verified against the code:
  1. **One 24h window for the whole snapshot** (plus a 60s `perMin`). The earlier "funnel
     stages = latest complete hour" + "open-failures = 7d backlog" model was dropped: it mixed
     time windows so the funnel conversion %s were meaningless. Every headline number and every
     funnel stage is now a 24h total; sparklines are the recent hourly buckets within it.
  2. **Pipeline scope = workflow runs only.** `openFailures`, the funnel `failed` stage,
     `failRatePct`, and `needsAttention` are all about **workflow-run** failures. Call-processing
     failures are **out of this screen** (they live on the calls view, which already has replay).
     Rationale: webhook *ingestion* cannot fail in a recorded way (`WebhookEventStatus` has only
     `RECEIVED`; bad/duplicate webhooks are rejected at the door, never persisted), so the only
     recorded, fixable failure on the pipeline is a failed run.
  3. **The dashboard is read-only in v1.** With calls gone and run-replay deferred, there is **no
     Replay action anywhere** on the dashboard. `needsAttention` is inspect-only (a row navigates
     to the run detail). Replay returns when run-replay is built (see Non-goals).
  4. **5-state health headline** driven by the 24h run success rate (was a 2-state count-based
     headline that never warned). See "Health headline model".
  5. **Open-failures trend arrow restored.** Because `openFailures` is now a 24h windowed count
     (not a rolling backlog), its delta vs the prior 24h is computable — all three hero tiles get
     real deltas.
  6. **Query layer = a dashboard-local JDBC read repository** (`DashboardMetricsReadRepository`
     port + `JdbcDashboardMetricsReadRepository` adapter), mirroring the existing
     `JdbcWebhookFeedReadRepository`. SQL lives in persistence, not the service. This is **not**
     the RD-012 `ReportingQuery` port (that is Phase 3). The doc's earlier "3 aggregator beans"
     are collapsed into the service + read repo.
  7. **UTC hourly buckets** (`date_trunc('hour', ts)` in UTC); the UI localises for display.
  8. **No schema change / no Flyway migration** — pure on-read aggregation over existing tables
     and indexes.
  9. **Snapshot computed in one `@Transactional(readOnly = true)` at `REPEATABLE READ`** so all
     queries see a single consistent DB snapshot and the cross-widget invariants hold by
     construction.

## Goal

Replace the redesigned operations dashboard's data layer (today: 4 client-side list calls
stitched with `systemHealth.mode='placeholder'`) with **one server-computed snapshot** so
every widget shows numbers that agree by construction. Operational pipeline health only —
not lead accountability (that's Phase 2).

## Why one snapshot (the core constraint)

The design demands internal consistency: every widget that touches "failures" must show the
same figure. So we compute each number **once**, server-side, in a single consistent read,
and return one `DashboardSnapshotDto`. The UI binds to that single object; widgets cannot
diverge. The single-snapshot read also makes the v1 invariant hold by construction:

> `hero.openFailures` == `funnel.failed.value` == `needsAttention` true count — all three are
> "FAILED workflow runs in the 24h window", one definition, one window, one query.

## Architecture (the slice)

```
GET /admin/dashboard/snapshot
        │
DashboardController                                  ← @RequestMapping("/admin/dashboard"), admin-auth
        │
DashboardSnapshotService.snapshot()                 ← @Transactional(readOnly=true, REPEATABLE_READ)
   │  computes ONE "now" + the 24h window + the 60s mark, passes them to every read
   ├─ DashboardMetricsReadRepository (port)          ← persistence layer; JDBC adapter owns the SQL
   └─ WorkflowRunQueryService.listRunsCrossWorkflow  ← REUSED for recentRuns (no new query)
        │ assembles
   DashboardSnapshotDto   ──▶  UI binds every widget to this one object
```

- **Placement:** `controller/DashboardController`, `service/reporting/dashboard/DashboardSnapshotService`,
  `persistence/repository/{DashboardMetricsReadRepository, JdbcDashboardMetricsReadRepository}`.
  The service is pre-placed under `reporting/` to shrink the Phase-3 re-home; it stays a plain
  service with no framework abstraction.
- **Auth:** `@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")`, same as `/admin/*`.
- **No query params in v1.** The window is fixed server-side; the client never asks for one.
- **Consistency:** the whole snapshot runs in one read-only `REPEATABLE READ` transaction so a
  replay/run landing mid-call cannot make two widgets disagree.

## The endpoint

- `GET /admin/dashboard/snapshot` — returns the full `DashboardSnapshotDto` in one response.
  Read-only; no `Result`-status wrapper needed (a DB failure surfaces as a 500 via the existing
  `/admin/*` handling — there is no global `@ControllerAdvice`; nothing to add for a read GET).
- UI: `useDashboardSnapshotQuery` collapses from 4 calls to **1**. **No polling** — see
  "Liveness & refresh".

## Query / DB layer (`DashboardMetricsReadRepository`)

A **dashboard-local** read repo — port + JDBC adapter, the same shape as
`JdbcWebhookFeedReadRepository` (`NamedParameterJdbcTemplate`, query/row records). All SQL is
`COUNT` / `GROUP BY date_trunc('hour', …)` over existing tables; **no new schema**. Every method
takes an explicit time range so the service controls the window.

```java
interface DashboardMetricsReadRepository {
  // status → count over [from,to); drives runs total, successRate, failed/openFailures,
  // funnel.runs.value, funnel.failed.value. Called twice (current + prior 24h) for deltas.
  Map<WorkflowRunStatus,Long> runStatusCounts(OffsetDateTime from, OffsetDateTime to);

  // hourly buckets (UTC) — value = sum over window, spark = last 8 buckets, series = all 24
  List<HourlyBucket> hourlyIngested(OffsetDateTime from, OffsetDateTime to);        // webhook_events.received_at
  List<HourlyBucket> hourlyDomainEvents(OffsetDateTime from, OffsetDateTime to);    // events.created_at
  List<HourlyBucket> hourlyRunsTotal(OffsetDateTime from, OffsetDateTime to);       // workflow_runs.created_at, excl DUPLICATE_IGNORED
  List<HourlyBucket> hourlyFailedRuns(OffsetDateTime from, OffsetDateTime to);      // workflow_runs.created_at, status=FAILED

  long countWebhookEventsSince(OffsetDateTime since);                               // perMin (now-60s)

  List<FailedRunRow> failedRuns(OffsetDateTime from, OffsetDateTime to, int limit); // needsAttention (cap 50)
}

record HourlyBucket(OffsetDateTime hour, long count) {}
record FailedRunRow(Long id, String workflowKey, String reasonCode, OffsetDateTime createdAt) {}
```

- **`recentRuns` reuses `WorkflowRunQueryService.listRunsCrossWorkflow(null, 0, 5)`** — no new
  query; the service maps each `WorkflowRunSummary` and adds `durationSec`.
- **Buckets are UTC** (`date_trunc('hour', ts)` evaluates in UTC). The 24h series always returns
  24 points; the UI's 12h/24h toggle is client-side slicing, not a refetch.
- **Indexes (existing, sufficient):** `workflow_runs(status, created_at)`,
  `webhook_events(received_at, id)`, `processed_calls(status, updated_at)`. Only `events.created_at`
  lacks a dedicated index; the windowed scan is acceptable at current volume (revisit at scale).

## Verified data ground truth (the facts the queries rest on)

- **`workflow_runs`:** `status` (enum `PENDING, BLOCKED, DUPLICATE_IGNORED, CANCELED, COMPLETED,
  FAILED`), `workflow_key` (de-normalised, no join), `reason_code`, `created_at`, `updated_at`.
  **No `started_at`** → `startedAt = created_at`, `completedAt = updated_at` (terminal only),
  `durationSec = updated_at − created_at`.
- **`webhook_events`:** `received_at`. Status enum is `RECEIVED` only — **ingestion cannot fail in
  a recorded way.**
- **`events`:** `created_at` (the domain-event diary).
- Success = `COMPLETED`; failure = `FAILED`; `DUPLICATE_IGNORED` is not a real execution and is
  excluded from `runs` totals and the success-rate denominator.

## `DashboardSnapshotDto` (the contract)

```jsonc
DashboardSnapshotDto {
  window: { from, to, label },                 // the 24h anchor, echoed back

  hero: {
    state: "ALL_CLEAR"|"HEALTHY"|"DEGRADED"|"UNHEALTHY"|"IDLE",
    openFailures: int,                         // FAILED runs in the 24h window
    stats: {
      runs:        { value: int,   delta: Delta },
      successRate: { value: float, delta: Delta },   // null when no terminal runs
      openFailures:{ value: int,   delta: Delta }
    }
  },

  throughput: { series: int[24], peak: int, avg: int, perMin: int },

  funnel: {
    ingested:     Stage,                       // all four stages are 24h totals
    domainEvents: Stage,
    runs:         Stage,
    failed:       Stage,
    conversions:  { normalizedPct: float|null, toRunsPct: float|null, failRatePct: float|null }
  },

  recentRuns:     RunRow[5],
  needsAttention: FailureRow[]                  // ≤ 50, newest first
}

Delta      { value: float|null, direction: "UP"|"DOWN"|"FLAT" }
Stage      { value: int, spark: int[8] }
RunRow     { id, workflowKey, status, startedAt, completedAt, durationSec }
FailureRow { ref, workflowKey, status, reason, ageSeconds }
```

## Contract reference — every field: meaning + how it is calculated

> Read this as the source of truth for what each value *is*. All times are ISO-8601 **UTC**
> strings. All "ages"/"durations" are **server-computed ints** (the UI never does clock math).
> All windows are computed once from a single server-side `now`.

### `window`
| Field | Meaning | Calculation |
|---|---|---|
| `from` | start of the reporting window | `now − 24h` |
| `to` | end of the window (= "as of" time) | `now` (the instant the query ran) |
| `label` | human label for the window | constant `"Last 24h"` in v1 |

### `hero`
| Field | Meaning | Calculation |
|---|---|---|
| `state` | the health headline word | tiered on the 24h success rate — see "Health headline model" |
| `openFailures` | failed runs to look at | `COUNT(workflow_runs WHERE status='FAILED' AND created_at ∈ [from,to))` |
| `stats.runs.value` | how many runs executed | `COUNT(workflow_runs in window) − DUPLICATE_IGNORED` |
| `stats.runs.delta` | run-volume trend | **percent change** vs the prior 24h `[now−48h, now−24h)`: `(cur−prev)/prev×100` |
| `stats.successRate.value` | % of finished runs that succeeded | `COMPLETED / (COMPLETED + FAILED) × 100` in window; `null` if `COMPLETED+FAILED = 0` |
| `stats.successRate.delta` | success-rate trend | **percentage-point** difference: `curRate − prevRate` |
| `stats.openFailures.value` | same as `hero.openFailures` | (repeated inside the stat strip) |
| `stats.openFailures.delta` | failure-count trend | **absolute count** change vs prior 24h: `cur − prev` (e.g. `-2`) |

`Delta.direction` is `UP`/`DOWN`/`FLAT` (sign of `value`). If the prior window's baseline is `0`
(nothing to compare), `Delta = { value: null, direction: "FLAT" }`.

### `throughput` (source: `webhook_events.received_at`)
| Field | Meaning | Calculation |
|---|---|---|
| `series` | hourly intake for the area chart | 24 buckets: `COUNT GROUP BY date_trunc('hour', received_at)` over the window |
| `peak` | busiest hour | `max(series)` |
| `avg` | typical hour | `mean(series)` (integer) |
| `perMin` | live intake readout | `COUNT(webhook_events WHERE received_at ≥ now − 60s)` |

### `funnel` (all four stage values are **24h totals**, same window → honest conversions)
| Field | Meaning | Calculation |
|---|---|---|
| `ingested.value` | webhooks received | `COUNT(webhook_events in window)` |
| `domainEvents.value` | domain events emitted | `COUNT(events in window)` |
| `runs.value` | workflow runs created | `COUNT(workflow_runs in window) − DUPLICATE_IGNORED` (= `hero.stats.runs.value`) |
| `failed.value` | runs that failed | `COUNT(workflow_runs FAILED in window)` (= `hero.openFailures`) |
| `<stage>.spark` | 8-point mini trend | the **last 8 hourly buckets** for that stage |
| `conversions.normalizedPct` | intake → domain events | `domainEvents.value / ingested.value × 100`; `null` if `ingested.value = 0` |
| `conversions.toRunsPct` | domain events → runs | `runs.value / domainEvents.value × 100`; `null` if `domainEvents.value = 0` |
| `conversions.failRatePct` | runs → failures | `failed.value / runs.value × 100`; `null` if `runs.value = 0` |

> Note: the funnel is **operational** (webhook_events → events → workflow_runs → FAILED), not the
> lead funnel (leads → assigned → called) — that is the Phase-2 accountability slice. Stages are
> counted over the same window but are **not a strict cohort** (a webhook in hour N may produce a
> run in hour N+1); over 24h the boundary effect is small. A conversion can read slightly oddly at
> low volume; that is acceptable for a health glance.

### `recentRuns` (reuses the existing cross-workflow runs query; 5 rows, newest first)
| Field | Meaning | Calculation |
|---|---|---|
| `id` | run id | `workflow_runs.id` |
| `workflowKey` | which workflow | `workflow_runs.workflow_key` |
| `status` | raw run status | `workflow_runs.status` (the UI maps to a badge) |
| `startedAt` | when it began | `created_at` |
| `completedAt` | when it ended | `updated_at` when terminal (`COMPLETED`/`FAILED`/`CANCELED`), else `null` |
| `durationSec` | how long it took | `updated_at − created_at` (seconds) when terminal, else `null` |

### `needsAttention` (failed runs only; view-only in v1; ≤ 50, newest first by `ageSeconds`)
| Field | Meaning | Calculation |
|---|---|---|
| `ref` | the run id (for navigation) | `workflow_runs.id` as string |
| `workflowKey` | which workflow failed | `workflow_runs.workflow_key` |
| `status` | raw status | always `FAILED` in v1 |
| `reason` | why it failed | `workflow_runs.reason_code` |
| `ageSeconds` | how long ago it failed | `now − created_at` (seconds), server-computed |

The list is capped at 50; the UI shows "+N more" where `N = hero.openFailures − needsAttention.length`.
A row is **view-only** (navigates to the run detail). There is **no Replay** on the dashboard in v1.

## Health headline model

`hero.state` is computed **server-side** from the 24h run success rate (so the thresholds live in
one place). The UI only styles the word and composes a subtitle from the numbers.

| `state` | Rule (24h window) | Intent |
|---|---|---|
| `IDLE` | no runs in the window | quiet — not asserting "healthy" |
| `ALL_CLEAR` | runs present, **0 failed** | pristine; nothing to do |
| `HEALTHY` | success rate **≥ 95%** | normal operation |
| `DEGRADED` | success rate **85–95%** | worth a look |
| `UNHEALTHY` | success rate **< 85%** | act now |

**Thresholds (85% / 95%) are tunable** — they are owner-set defaults, kept as named constants so
they can be changed without touching the contract. Suggested subtitle (UI-composed):
e.g. "42 of 300 runs failed in the last 24h".

## Definitions & decisions (locked)

- **Success rate = `COMPLETED / (COMPLETED + FAILED)`.** Confirmed by the design's own numbers:
  `289 / (289 + 9) = 97.0%`. Excludes `PENDING/CANCELED/BLOCKED/DUPLICATE_IGNORED` from the
  denominator.
- **`runs` total excludes `DUPLICATE_IGNORED`** (not a real execution).
- **Pipeline scope = workflow runs only.** No call failures, no ingestion failures (none exist).
- **One 24h window** for hero stats, funnel stages, and throughput; **60s** for `perMin`. Deltas
  compare against the prior equal 24h window `[now−48h, now−24h)`.
- **Read-only dashboard.** No Replay; `needsAttention` rows navigate to run detail.
- **Status → badge mapping lives in the UI.** The DTO returns the raw enum string
  (`COMPLETED`, `FAILED`, `DUPLICATE_IGNORED`, …); the UI owns `COMPLETED→Succeeded`,
  `PENDING→Running`, `DUPLICATE_IGNORED→` muted "Duplicate", etc. Keeps the BE contract stable.

## Contract edge cases (locked)

- **Zero denominators → `null`** (the UI renders `—`, never `0`/`NaN`): `successRate.value` when no
  terminal runs; any `Delta` whose prior baseline is `0` → `{ value: null, direction: "FLAT" }`;
  any `conversions.*` whose denominator is `0`.
- **List sizes:** `recentRuns` = **5** (fixed); `needsAttention` capped at **50**, newest first.
  `hero.openFailures` stays the *true* count (may exceed 50); "+N more" = `openFailures − length`.
- **`window` is fixed 24h in v1** — not a client param. The server always returns 24 hourly points;
  the design's 12h/24h toggle is client-side slicing.
- **Serialization:** timestamps are ISO-8601 UTC strings; `ageSeconds`/`durationSec` are
  server-computed ints; percentages are numbers (e.g. `97.0`), not pre-formatted strings.
- **Cold start / no data:** all counts `0`, rates/deltas/conversions `null`, `state = IDLE`.
- **Errors** inherit the existing `/admin/*` behaviour — no bespoke error shape.

## Frontend

- Collapse `useDashboardSnapshotQuery` to one call — **no `refetchInterval`** (event-driven; see
  "Liveness & refresh").
- Expand the `DashboardSnapshot` TS type to mirror the DTO (Zod schema per repo convention),
  including the nullable fields above.
- Two new SVG chart components: **AreaChart** (throughput) and **Sparkbars** (funnel) — thin,
  token-driven, **custom (no chart lib)** per
  [RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md). These are a **port**
  of `charts.jsx` (`AreaChart` + `Sparkbars`), JSX→TSX, inline-styles→tokens. Keep `smoothPath`
  (Catmull-Rom), `useId()` for unique gradient ids, `preserveAspectRatio="none"` +
  `vectorEffect="non-scaling-stroke"`, and guard the all-zero/flat series. Dashboard-local for now;
  promote to `shared/ui` on a second consumer.
- Recreate hero / funnel rail / recent-runs / needs-attention per the handoff, reusing existing
  `shared/ui/*` (Button, Badge, DataTable, toast). Pulse dot = CSS. **No replay/confirm flow** in
  v1 (the worklist is view-only).
- A small **"updated {time}"** label (from `window.to`) so the point-in-time figures read honestly
  under the always-pulsing dot.
- The health headline word + colour is driven by `hero.state`; the subtitle is composed from the
  hero stats.

## Liveness & refresh (v1)

**v1 does not poll.** The snapshot is refreshed by **events, not a timer**:

- **on mount** — fetched when the dashboard opens;
- **on window focus** — `refetchOnWindowFocus` (TanStack default, kept on) refetches when the
  operator tabs back.

No `refetchInterval`. The **"live" feeling is the CSS pulse dot** (continuous, no data behind it),
not the number's refresh rate. `events/min` (`throughput.perMin`) is a plain field in the snapshot,
frozen at fetch time and refreshed on the same events — honest because of the "updated {time}"
label. (In v1 there is no replay-driven invalidation, since the dashboard performs no actions.)

**Going live later is a deferred, isolated change — no machinery built now.** The
single-snapshot-object boundary behind one hook *is* the switch: time-based liveness → add
`refetchInterval` (one line); true push → swap the hook's source to an SSE subscription emitting
snapshots (UI unchanged; SSE infra already exists via `webhookStreamPort`); independent fast pulse
→ promote `perMin` to its own `GET /admin/dashboard/pulse`. The trigger to do any of these is an
**access-pattern divergence** that does not exist in v1.

## Order of work (sub-steps within Phase 1)

1. **Backend read repo** — `DashboardMetricsReadRepository` + `JdbcDashboardMetricsReadRepository`
   + a **Testcontainers Postgres IT** (the SQL is the risk: `date_trunc`/window math can't be
   faithfully unit-tested on H2 — prove it on real Postgres first).
2. **Backend snapshot endpoint** — `DashboardSnapshotDto` + `DashboardSnapshotService`
   (`@Transactional(readOnly=true, REPEATABLE_READ)`) + `DashboardController`. Service unit tests
   (delta / null / state-tier logic with a mocked read repo) + the invariant IT.
3. **Frontend data layer** — one query, expanded type/Zod, snapshot binding.
4. **Frontend presentation** — hero (with 5-state headline), funnel, recent runs, needs-attention
   (view-only); AreaChart + Sparkbars.

## Non-goals (deferred)

- **Run-replay + a run "resolved/handled" flag** — net-new, semantically hard (step-level resume /
  idempotent side effects). Its own future mini-project. **When it lands, Replay returns to the
  `needsAttention` worklist.**
- **A persistent open-failures backlog** (failures that stay "open" until acknowledged, regardless
  of age) — needs a resolve/acknowledge action (new capture). v1 uses the 24h window instead.
- **Call-processing failures on this screen** — they belong to the calls view (which already has
  replay). Could be folded into a future unified worklist if desired.
- The framework (`ReportProvider` etc.) — Phase 3.
- Lead/accountability metrics — Phase 2.
- Any time-based polling / live `events/min` — v1 is event-driven (mount / focus).
- Pre-computed/materialized aggregates — on-read is fine at this scale.

## Risks (with detection)

- **Funnel ratios not reconciling** if reads use different windows. *Detect:* the consistency IT.
  *Mitigation:* single shared window + one `REPEATABLE READ` transaction.
- **Mid-call data change** making two widgets disagree. *Detect:* invariant IT
  (`openFailures == funnel.failed.value`). *Mitigation:* the one repeatable-read transaction.
- **`events.created_at` scan cost** as history grows (no dedicated index). *Detect:* query timing.
  *Mitigation:* add an index / materialise later (out of scope now).
- **Health thresholds wrong for this team.** *Detect:* operator feedback. *Mitigation:* thresholds
  are named, tunable constants — no contract change to adjust them.

## Validation criteria

- `GET /admin/dashboard/snapshot` returns one coherent payload; **`hero.openFailures ==
  funnel.failed.value`** and the conversion %s reconcile against the stage values (invariant test).
- Success rate matches `COMPLETED/(COMPLETED+FAILED)` on a seeded fixture; `successRate` is `null`
  when there are no terminal runs.
- Deltas compute against the prior 24h; a `0` baseline yields `{ value: null, direction: "FLAT" }`.
- `hero.state` tiers correctly across seeded fixtures (IDLE / ALL_CLEAR / HEALTHY / DEGRADED /
  UNHEALTHY).
- Dashboard renders the handoff design with real data; `events/min` shows a real value; the snapshot
  refreshes on mount / window-focus (no timer), with an "updated {time}" label and a continuous CSS
  pulse dot; the `needsAttention` list is view-only.

## Linked

- [plan.md](./plan.md) (Phase 1) · [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
  · [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) (framework deferred to Phase 3)
  · [RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md) (custom charts).
