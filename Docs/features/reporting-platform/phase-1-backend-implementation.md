# Reporting Phase 1 (Backend) — Operations Dashboard snapshot

> **Status: implementation plan — not yet built.** The **backend slice** of the Phase-1 dashboard:
> one controller + a snapshot service + a dashboard-local JDBC read repository + a DTO. **No
> framework** (`ReportProvider`/`ReportingQuery`/registry are deferred to Phase 3 per
> [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md)). The read repo is
> dashboard-local and must NOT be generalised here. **Build this before the frontend.**
>
> This doc **owns the `DashboardSnapshotDto` contract**; the frontend doc
> ([phase-1-frontend-implementation.md](./phase-1-frontend-implementation.md)) consumes it.
> Data sources verified in [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
> "Verified backend ground truth". Plan: [plan.md](./plan.md) (Phase 1).

## Changelog

- **2026-06-24 — split BE/FE.** The combined `phase-1-implementation.md` was split into this backend
  doc (endpoint + contract, built first) and a frontend doc (consumes the contract). Content
  unchanged from rev 4 below.
- **2026-06-24 (rev 4) — hard-eval fixes (consult).** Two changes from an adversarial, code-grounded
  review: (a) **Removed the funnel conversion percentages** (`normalizedPct`/`toRunsPct`/`failRatePct`).
  The pipeline is many-to-many (one webhook → 0..N domain events; one event → 0..N runs; many webhooks
  are intentionally ignored), so those ratios read structurally **<100% or >100% in a healthy system**
  and look like bugs. The funnel now shows the **four stage counts + sparkbars** only ("how many came
  in, how many reached each next step"); run health is the **headline's** job (success rate). (b) **Made
  the consistency guarantee explicit:** the snapshot tx is `@Transactional(readOnly=true,
  isolation=REPEATABLE_READ, propagation=REQUIRED)` and the IT asserts it under a concurrent writer —
  `REPEATABLE_READ` is new to this codebase and there's a `REQUIRES_NEW` history (#31), so it can't be
  left implicit. Minor: invariant wording (uncapped count) and `phases.md` 3-state sync.
- **2026-06-24 (rev 3) — verified against code + two locks.** Whole spec re-verified file-by-file
  against the codebase (see "Verified data ground truth" + the field/index notes below). Two open
  details closed: (a) **health headline collapsed 5→3 states** (`HEALTHY`/`DEGRADED`/`UNHEALTHY`);
  (b) **`now` is floored to the top of the hour** so the window is exactly 24 whole hour-aligned
  buckets (≤1h staleness accepted), and **zero-fill of empty hours happens in the service, not SQL**.
  See "Bucketing & window math".
- **2026-06-19 (rev 2) — backend design finalised (this is the build-ready spec).** Decisions
  taken in a design session, all verified against the code:
  1. **One 24h window for the whole snapshot** (plus a 60s `perMin`). The earlier "funnel
     stages = latest complete hour" + "open-failures = 7d backlog" model was dropped: it mixed
     time windows so the funnel numbers were meaningless. Every headline number and every
     funnel stage is now a 24h total; sparklines are the recent hourly buckets within it.
  2. **Pipeline scope = workflow runs only.** `openFailures`, the funnel `failed` stage, and
     `needsAttention` are all about **workflow-run** failures. Call-processing failures are **out of
     this screen** (they live on the calls view, which already has replay). Rationale: webhook
     *ingestion* cannot fail in a recorded way (`WebhookEventStatus` has only `RECEIVED`;
     bad/duplicate webhooks are rejected at the door, never persisted), so the only recorded, fixable
     failure on the pipeline is a failed run.
  3. **The dashboard is read-only in v1.** With calls gone and run-replay deferred, there is no
     Replay action anywhere on the dashboard. `needsAttention` is inspect-only. Replay returns when
     run-replay is built (see Non-goals).
  4. **3-state health headline** (`HEALTHY`/`DEGRADED`/`UNHEALTHY`) driven by the 24h run success
     rate. See "Health headline model".
  5. **Open-failures trend arrow restored.** Because `openFailures` is now a 24h windowed count
     (not a rolling backlog), its delta vs the prior 24h is computable — all three hero tiles get
     real deltas.
  6. **Query layer = a dashboard-local JDBC read repository** (`DashboardMetricsReadRepository`
     port + `JdbcDashboardMetricsReadRepository` adapter), mirroring the existing
     `JdbcWebhookFeedReadRepository`. SQL lives in persistence, not the service. This is **not**
     the RD-012 `ReportingQuery` port (that is Phase 3).
  7. **UTC hourly buckets** (`date_trunc('hour', ts)` in UTC); the UI localises for display.
  8. **No schema change / no Flyway migration** — pure on-read aggregation over existing tables
     and indexes.
  9. **Snapshot computed in one read-only `REPEATABLE READ` transaction** so all queries see a
     single consistent DB snapshot and the cross-widget invariants hold by construction.

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

> `hero.openFailures` == `funnel.failed.value` == `needsAttention`'s **uncapped** total — all three
> are "FAILED workflow runs in the 24h window", one definition, one window, one query. (The
> `needsAttention` array is capped at 50 for display; the invariant is on the total count, not the
> array length.)

## Architecture (the slice)

```
GET /admin/dashboard/snapshot
        │
DashboardController                                  ← @RequestMapping("/admin/dashboard"), admin-auth
        │
DashboardSnapshotService.snapshot()                 ← @Transactional(readOnly=true, isolation=REPEATABLE_READ, propagation=REQUIRED)
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
- **Consistency (must be explicit — this is the codebase's first `REPEATABLE READ`):** the snapshot
  method declares `@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ,
  propagation=Propagation.REQUIRED)` — **not** just `readOnly=true` (verified: nothing in the repo
  sets `REPEATABLE_READ` today, so the default `READ_COMMITTED` would let two `COUNT`s see different
  data). The reused `WorkflowRunQueryService.listRunsCrossWorkflow` is `@Transactional(readOnly=true)`
  with default `REQUIRED` today, so it **joins** the outer snapshot — it must **not** be switched to
  `REQUIRES_NEW` (the repo has that pattern + the #31 history; pin this with a comment). The JDBC read
  repo shares the same `DataSource`/`PlatformTransactionManager`, so it reads within the same snapshot.
  The IT proves the isolation actually holds (invariant under a concurrent writer).

## The endpoint

- `GET /admin/dashboard/snapshot` — returns the full `DashboardSnapshotDto` in one response.
  Read-only; no `Result`-status wrapper needed (a DB failure surfaces as a 500 via the existing
  `/admin/*` handling — there is no global `@ControllerAdvice`; nothing to add for a read GET).

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
  query. *(Verified)* The returned `WorkflowRunSummary` is a record
  `{ id, workflowKey, workflowVersionNumber, status, reasonCode, startedAt, completedAt }` — it
  **already** exposes `startedAt` (= `created_at`) and `completedAt` (= `updated_at`, and is `null`
  for `PENDING`). There are **no `created_at`/`updated_at` fields by those names** — bind to
  `startedAt`/`completedAt`. The service only derives `durationSec = completedAt − startedAt`. (If
  strict terminal-only `completedAt` is wanted, also null it for `BLOCKED` in the mapper — the
  summary nulls only `PENDING`.)
- **Buckets are UTC** (`date_trunc('hour', ts)` evaluates in UTC). The 24h series always returns
  24 points (see "Bucketing & window math"); the chart always shows the full 24h (no range toggle in v1).
- **Indexes (existing, verified sufficient):** `workflow_runs(status, created_at)` (V10);
  `webhook_events(received_at DESC, id DESC)` (V3 — DESC, fine for range counts);
  `processed_calls(status, updated_at)` (V2). `events.created_at` has **no standalone index** (only
  a `(kind, created_at)` composite, whose leading `kind` can't serve a `created_at`-only range);
  the windowed scan is acceptable at current volume (revisit at scale).

### Bucketing & window math (locked)

- **`now` is floored to the top of the hour** — `flooredNow = date_trunc('hour', now)` — before
  anything is computed. The window is `[flooredNow − 24h, flooredNow)` = exactly **24 whole,
  hour-aligned buckets, no partials**. The board is "as of the top of the hour" (≤1h stale) — an
  accepted trade for clean buckets and stable deltas. Delta windows use the same anchor: prior =
  `[flooredNow − 48h, flooredNow − 24h)`. `perMin` is the **only** sub-hour value and uses the true
  `now − 60s`.
- **Zero-fill happens in the service, not SQL.** `GROUP BY date_trunc('hour', …)` returns rows only
  for hours that had data. The service generates the 24 hour-slots from the floored window and maps
  each query result onto them, filling absent hours with `0`, so every `series`/`spark` is a dense
  24- (or 8-) point array. This keeps the SQL plain Postgres (portable for the Testcontainers IT) —
  no `generate_series` left-join.

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
    state: "HEALTHY"|"DEGRADED"|"UNHEALTHY",
    openFailures: int,                         // FAILED runs in the 24h window
    stats: {
      runs:        { value: int,   delta: Delta },
      successRate: { value: float, delta: Delta },   // null when no terminal runs
      openFailures:{ value: int,   delta: Delta }
    }
  },

  throughput: { series: int[24], peak: int, avg: int, perMin: int },

  funnel: {                                    // four independent 24h volume counts — NO conversion %s
    ingested:     Stage,
    domainEvents: Stage,
    runs:         Stage,
    failed:       Stage
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
| `from` | start of the reporting window | `flooredNow − 24h` |
| `to` | end of the window (= "as of" time) | `flooredNow` = `date_trunc('hour', now)` (top of the hour) |
| `label` | human label for the window | constant `"Last 24h"` in v1 |

### `hero`
| Field | Meaning | Calculation |
|---|---|---|
| `state` | the health headline word (`HEALTHY`/`DEGRADED`/`UNHEALTHY`) | tiered on the 24h success rate — see "Health headline model" |
| `openFailures` | failed runs to look at | `COUNT(workflow_runs WHERE status='FAILED' AND created_at ∈ [from,to))` |
| `stats.runs.value` | how many runs executed | `COUNT(workflow_runs in window) − DUPLICATE_IGNORED` |
| `stats.runs.delta` | run-volume trend | **percent change** vs the prior 24h `[now−48h, now−24h)`: `(cur−prev)/prev×100` |
| `stats.successRate.value` | % of finished runs that succeeded | `COMPLETED / (COMPLETED + FAILED) × 100` in window; `null` if `COMPLETED+FAILED = 0` |
| `stats.successRate.delta` | success-rate trend | **percentage-point** difference: `curRate − prevRate` |
| `stats.openFailures.value` | same as `hero.openFailures` | (repeated inside the stat strip) |
| `stats.openFailures.delta` | failure-count trend | **absolute count** change vs prior 24h: `cur − prev` (e.g. `-2`) |

`Delta.direction` is `UP`/`DOWN`/`FLAT` (sign of `value`). For the **ratio** deltas (`runs`
percent-change, `successRate` percentage-point) a zero/absent prior baseline (prior runs `0`, or no
prior terminal runs) yields `{ value: null, direction: "FLAT" }`. The **absolute `openFailures`
delta** is always `cur − prev` — a `0 → N` failure spike shows `UP +N` and is never suppressed.

### `throughput` (source: `webhook_events.received_at`)
| Field | Meaning | Calculation |
|---|---|---|
| `series` | hourly intake for the area chart | 24 buckets: `COUNT GROUP BY date_trunc('hour', received_at)` over the window |
| `peak` | busiest hour | `max(series)` |
| `avg` | typical hour | `mean(series)` (integer) |
| `perMin` | live intake readout | `COUNT(webhook_events WHERE received_at ≥ now − 60s)` — **true `now`**, not floored |

### `funnel` (four independent **24h volume counts** — no conversion %s)
| Field | Meaning | Calculation |
|---|---|---|
| `ingested.value` | webhooks received | `COUNT(webhook_events in window)` |
| `domainEvents.value` | domain events emitted | `COUNT(events in window)` |
| `runs.value` | workflow runs created | `COUNT(workflow_runs in window) − DUPLICATE_IGNORED` (= `hero.stats.runs.value`) |
| `failed.value` | runs that failed | `COUNT(workflow_runs FAILED in window)` (= `hero.openFailures`) |
| `<stage>.spark` | 8-point mini trend | the **last 8 hourly buckets** for that stage |

> Note: the funnel is a **volume breakdown** — "how many webhooks came in, how many domain events,
> how many runs, how many failed" over the same 24h window. **No conversion percentages:** the
> pipeline is many-to-many (one webhook can emit 0..N domain events; one event can trigger 0..N runs;
> many webhooks are intentionally ignored), so a "% converted" would read structurally <100% or >100%
> even when everything is healthy. Run health is the **headline's** job (success rate); the funnel
> just shows where the volume is. It is **operational** (webhook_events → events → workflow_runs →
> FAILED), not the lead funnel (Phase-2). Stages are not a strict cohort (a webhook in hour N may
> produce a run in hour N+1).

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
| `reason` | why it failed | `workflow_runs.reason_code` — **nullable** (verified: a FAILED run may have none); emit `null`, UI renders "—"/"Unknown" |
| `ageSeconds` | how long ago it failed | `now − created_at` (seconds), server-computed |

The list is capped at 50; the UI shows "+N more" where `N = hero.openFailures − needsAttention.length`.
A row is **view-only** (navigates to the run detail). There is **no Replay** on the dashboard in v1.

## Health headline model

`hero.state` is computed **server-side** from the 24h run success rate (so the thresholds live in
one place). The UI only styles the word and composes a subtitle from the numbers.

| `state` | Rule (24h window) | Intent |
|---|---|---|
| `HEALTHY` | success rate **≥ 95%**, **or** no terminal runs in the window (nothing failing) | normal / quiet |
| `DEGRADED` | success rate **85% ≤ rate < 95%** | worth a look |
| `UNHEALTHY` | success rate **< 85%** | act now |

Evaluation order: if there are **no terminal runs** (`COMPLETED + FAILED = 0`, so `successRate.value`
is `null`) → `HEALTHY`; otherwise tier on the rate. The "no runs" and "zero failures" cases both
land in `HEALTHY` — green is the resting state.

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

- **Zero / absent baseline:** `successRate.value` is `null` when no terminal runs (UI renders `—`).
  **Ratio deltas** (`runs` %, `successRate` pp) → `{ value: null, direction: "FLAT" }` on a zero/absent
  prior baseline. The **absolute `openFailures` delta** is always `cur − prev` (a `0 → N` spike shows
  `UP +N`, never suppressed).
- **List sizes:** `recentRuns` = **5** (fixed); `needsAttention` capped at **50**, newest first.
  `hero.openFailures` stays the *true* count (may exceed 50); "+N more" = `openFailures − length`.
- **`window` is fixed 24h in v1** — not a client param. The server always returns 24 hourly points.
- **Serialization:** timestamps are ISO-8601 UTC strings; `ageSeconds`/`durationSec` are
  server-computed ints; percentages are numbers (e.g. `97.0`), not pre-formatted strings.
- **Cold start / no data:** all counts `0`, rates/deltas `null`, `state = HEALTHY`
  (no terminal runs → nothing failing).
- **Errors** inherit the existing `/admin/*` behaviour — no bespoke error shape.

## Order of work (backend sub-steps)

1. **Read repo** — `DashboardMetricsReadRepository` + `JdbcDashboardMetricsReadRepository`
   + a **Testcontainers Postgres IT** (the SQL is the risk: `date_trunc`/window math can't be
   faithfully unit-tested on H2 — prove it on real Postgres first). Copy the IT skeleton from
   `WorkflowAdminApiIntegrationTest` (existing Testcontainers Postgres 16 setup + Flyway).
2. **Snapshot endpoint** — `DashboardSnapshotDto` + `DashboardSnapshotService`
   (`@Transactional(readOnly=true, isolation=REPEATABLE_READ, propagation=REQUIRED)`) +
   `DashboardController`. Service unit tests (delta / null / state-tier logic with a mocked read repo)
   + the invariant IT **with a concurrent writer** (proves the isolation, not just the arithmetic).

When both steps are green, hand off to
[phase-1-frontend-implementation.md](./phase-1-frontend-implementation.md).

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
- Pre-computed/materialized aggregates — on-read is fine at this scale.

## Risks (with detection)

- **Mid-call data change** making two widgets disagree. *Detect:* invariant IT
  (`openFailures == funnel.failed.value`). *Mitigation:* the one repeatable-read transaction.
- **`events.created_at` scan cost** as history grows (no dedicated index). *Detect:* query timing.
  *Mitigation:* add an index / materialise later (out of scope now).
- **Health thresholds wrong for this team.** *Detect:* operator feedback. *Mitigation:* thresholds
  are named, tunable constants — no contract change to adjust them.
- **Consistency guarantee silently not applied** — `REPEATABLE_READ` is new here, and the reused
  query could later be flipped to `REQUIRES_NEW` (repo precedent + #31), which would break the
  single-snapshot read. *Detect:* the invariant IT run with a concurrent writer. *Mitigation:*
  explicit `isolation`+`propagation` on the method + a pinning comment on the reused query.

## Validation criteria

- `GET /admin/dashboard/snapshot` returns one coherent payload; **`hero.openFailures ==
  funnel.failed.value == needsAttention`'s uncapped total** holds even under a concurrent writer
  (invariant + isolation IT). The funnel returns the four stage counts only (no conversion %s).
- Success rate matches `COMPLETED/(COMPLETED+FAILED)` on a seeded fixture; `successRate` is `null`
  when there are no terminal runs.
- Deltas compute against the prior 24h; a `0` baseline yields `{ value: null, direction: "FLAT" }`.
- `hero.state` tiers correctly across seeded fixtures (HEALTHY / DEGRADED / UNHEALTHY), including
  the no-terminal-runs case (→ HEALTHY).

## Linked

- Frontend slice: [phase-1-frontend-implementation.md](./phase-1-frontend-implementation.md)
- [plan.md](./plan.md) (Phase 1) · [dashboard-reporting-needs.md](./dashboard-reporting-needs.md)
  · [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md) (framework deferred to Phase 3).
