# Dashboard Reporting Needs — research note

> **Status: research only. No plan, no phases, nothing implemented.**
> This is *one slice* of the reporting platform, not the feature itself. It captures
> what reporting data the redesigned operations dashboard would need. Originally derived
> from the design handoff; **backend sources now verified against the code (2026-06-19)** —
> see "Verified backend ground truth" below. It commits us to nothing.

## Why this note exists

A high-fidelity redesign of the operator dashboard already exists as a prototype:
`ui/Flux Design System/design_handoff_dashboard/` (Direction A —
"Health headline"). Its README says to keep the data layer and replace the
presentation — but the prototype's numbers are **all hard-coded mock data**. The
design *assumes* a reporting layer that does not exist yet.

This note inventories that assumed reporting layer so the work is visible when we
plan. The dashboard is a consumer of reporting; building it surfaces the first,
smallest set of aggregation primitives the broader platform will need anyway.

## What the design is

A redesign of the existing operator dashboard (`ui/src/modules/dashboard/`). One
scrollable column, three stacked sections, answering "is the pipeline healthy right
now?". It drops the shell's panel/inspector — full-width overview.

## Components in the design (4 sections)

| # | Section | Visual components | Data it consumes |
|---|---------|-------------------|------------------|
| 1 | Hero — pipeline status | Headline, live pill, subtitle, 3-stat strip with up/down deltas, AreaChart (throughput), live events/min readout | Last-24h totals + per-period deltas + a 24-point hourly series |
| 2 | Funnel rail | 4 stage blocks each with value + Sparkbars (8-bar mini), 3 connectors with conversion % | 4 stage counts (same window) + 8-pt mini-series each + ratios |
| 3a | Recent Runs | DataTable (ledger), status badges, duration | Last 5 runs (id, workflow, status, started, completed) |
| 3b | Needs attention | Failure worklist rows, Replay → Confirm → Toast, empty state | Open (unreplayed) failures: kind, ref, workflow, reason, retries, age |

Chart primitives actually used in Direction A: **AreaChart** and **Sparkbars**.
(`Sparkline`, `Donut`, `HBars` exist in `charts.jsx` but feed other directions.)

## Reporting capabilities the design needs (vs. what exists today)

Distilling every number in the design into distinct data capabilities, graded
against the current codebase (which only does paginated list calls returning a
`total` and the first N rows):

| | Capability | Where it's used | Status today |
|---|---|---|---|
| **A** | Windowed aggregate counts (last-24h totals, `GROUP BY status`) | Hero stats (Runs 312, success 97.0%), outcome counts | **Missing.** Current dashboard reads all-time `total` via a `size:1` list — no window, no grouping. |
| **B** | Time-bucketed hourly series (`date_trunc('hour', …)`) | Throughput AreaChart (24 pts), funnel sparkbars (8 pts) | **Missing.** No time-series query exists anywhere. |
| **C** | Period-over-period deltas (current vs. prior window) | `+8%`, `+0.3pt`, `−2 today` | **Missing.** |
| **D** | Funnel stage counts + conversion across tables | Ingested → Domain Events → Workflow Runs → Failed, with ratios | **Missing.** Spans `webhook_events` + domain-events layer + `workflow_runs` aligned to one window. The most structurally novel piece. |
| **E** | Open-failures triage worklist (runs + calls, with reason/retries/age) | "Needs attention" list + Replay | **Partial.** Failed-runs list exists but is runs-only and lacks reason/retries; design unifies runs+calls. Replay exists for calls, **not yet for runs**. |
| **F** | Recent runs list | Recent Runs table | **Exists.** Straight reuse. |
| **G** | Live events/min readout | Hero live readout | Cosmetic; derive from B or a small poll. Lowest priority. |

## Shape of the work (for this slice only)

For the dashboard slice — **not** the full analytics platform:

- **Backend:** a dashboard-metrics endpoint (or a few) returning windowed status
  counts, a 24-pt hourly throughput series, funnel stage counts, and deltas. This is
  `COUNT` / `GROUP BY` / `date_trunc` over `webhook_events` + `workflow_runs` —
  **on-read aggregation, no new schema** for this slice. (The "pre-computed
  summaries vs. dedicated schema" question only bites at larger scale.)
- **Frontend:** AreaChart + Sparkbars as **custom token-driven SVG** (a port of `charts.jsx`,
  no chart library — [RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md);
  a library, lean visx, is for the later analytical layer), an expanded `DashboardSnapshot`
  type, the runs+calls open-failures worklist, and a run-status → design-badge mapping (the
  run enum doesn't match the design's labels — see Decisions). Run-replay is a pending
  decision (below); v1 likely ships calls-replayable with failed runs read-only.

Key observation: **everything the design needs is derivable from data already
stored** — pure aggregation, no new event capture. The funnel (capability D) is the
only piece that crosses table boundaries and deserves a real design decision when we
plan.

## Verified backend ground truth (2026-06-19)

Confirmed against the code — the capability grades above are no longer design-only guesses:

- **`workflow_runs`** — has `status`, `workflow_key` (de-normalized, no join), `created_at`,
  `updated_at`. Status enum: `PENDING, BLOCKED, DUPLICATE_IGNORED, CANCELED, COMPLETED,
  FAILED`. Windowed counts, success rate (`COMPLETED`/total), status breakdown, and recent
  runs all computable. **No `started_at`** — duration = `updated_at − created_at`,
  "completed" = `updated_at` (an approximation).
- **`webhook_events`** (`received_at`) and **`events`** (`created_at`) — both carry the
  timestamps for hourly bucketing. The operational funnel
  `webhook_events → events → workflow_runs → FAILED` is countable per window.
- **`processed_calls`** — `status` (FAILED), `failure_reason`, `retry_count`, `call_id`;
  call-replay endpoint exists (`POST /admin/processed-calls/{id}/replay`).
- **No aggregation exists today.** The current dashboard is four client-side list calls
  stitched in `dashboardSnapshot.ts` with `systemHealth.mode='placeholder'`. Phase 1 builds
  the first real aggregation endpoint.

## Decisions to make (Phase 1)

1. **Run-replay (the one real gap).** Workflow runs have **no** replay endpoint, no
   `retry_count`, and **no resolved/handled flag** — a FAILED run is terminal, and "open
   failures" for runs can only mean `status=FAILED` with no way to clear one. The design's
   worklist gives runs a Replay button and recomputes the headline on replay (works for
   calls, not runs). Options:
   - **(b, recommended)** v1 worklist = **calls replayable, failed runs read-only** (their
     "Replay" opens the run detail). Keeps the design intact; no new run infra.
   - (a) build run-replay + a resolution flag — its own mini-project; defer.
   - (c) worklist = calls only for v1.
2. **Run status → design badge mapping** — enum (`COMPLETED/PENDING/FAILED/CANCELED/BLOCKED/
   DUPLICATE_IGNORED`) vs. design labels (`Succeeded/Running/Canceled/Blocked`). Decide
   `COMPLETED→Succeeded`, `PENDING→Running`, and where `DUPLICATE_IGNORED` lands.

## Open questions (deferred)

- On-read aggregation vs. pre-computed summaries — fine on-read for this slice; revisit at scale.
- The funnel's three-table alignment to a single window (boundary handling across
  `received_at` vs `created_at`).

## Source material

- Design: `ui/Flux Design System/design_handoff_dashboard/` (README + `prototype/dashboard-app.jsx`, `prototype/dash-data.jsx`, `prototype/charts.jsx`)
- Current implementation: `ui/src/modules/dashboard/` (`DashboardPage.tsx`, `data/useDashboardSnapshotQuery.ts`, `lib/dashboardSnapshot.ts`)
