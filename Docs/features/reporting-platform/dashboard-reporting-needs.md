# Dashboard Reporting Needs — research note

> **Status: research only. No plan, no phases, nothing implemented.**
> This is *one slice* of the reporting platform, not the feature itself. It captures
> what reporting data the redesigned operations dashboard would need, derived purely
> from analysing the existing design handoff. It commits us to nothing.

## Why this note exists

A high-fidelity redesign of the operator dashboard already exists as a prototype:
`ui/Automation Engine Design System/design_handoff_dashboard/` (Direction A —
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
- **Frontend:** AreaChart + Sparkbars SVG components, an expanded `DashboardSnapshot`
  type, and the runs+calls worklist with run-replay.

Key observation: **everything the design needs is derivable from data already
stored** — pure aggregation, no new event capture. The funnel (capability D) is the
only piece that crosses table boundaries and deserves a real design decision when we
plan.

## Open questions (deferred to planning)

- On-read aggregation vs. pre-computed summaries — fine on-read for this slice; revisit at scale.
- The funnel's three-table alignment (webhook events / domain events / workflow runs to one window).
- Run-replay action + snapshot invalidation (mirror the existing call-replay path).

## Source material

- Design: `ui/Automation Engine Design System/design_handoff_dashboard/` (README + `prototype/dashboard-app.jsx`, `prototype/dash-data.jsx`, `prototype/charts.jsx`)
- Current implementation: `ui/src/modules/dashboard/` (`DashboardPage.tsx`, `data/useDashboardSnapshotQuery.ts`, `lib/dashboardSnapshot.ts`)
