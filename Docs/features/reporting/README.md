# Reporting — module foundation + health dashboard (first surface)

> **Status:** Grooming — research complete, implementation not started.
> **Scope:** Full-stack. Establish a **reporting module** (backend reporting model + FE reporting data seam),
> then ship the **health dashboard redesign** as its first consumer.
> **Design handoff:** `ui/Automation Engine Design System/design_handoff_dashboard/` (Direction A — "Health headline").

**Reporting is a domain, not a page.** This feature stands up a reporting capability that *any* frontend surface
can read from, and delivers the redesigned operations dashboard as **report surface #1**. It is structured so a
new report type is an **additive drop-in** — a new query + DTO + endpoint method on the backend, a new schema +
hook + page on the frontend — never a refactor.

### Anticipated future surfaces (out of scope here; the structure must accommodate them)
- A dedicated **reporting page** — e.g. "what actions did workflows take," "how many agents did what / agent
  activity," over selectable windows.
- More report types after that.

The dashboard must **consume** the reporting model, never own it.

## Architecture intent (the structural rule)
- **Backend — a reporting module hosting many reports.** `/admin/reporting/**` namespace; a report service per
  report type over a **shared** reporting read-model with reusable time-window / hourly-bucket / delta helpers.
  `…/dashboard` ships now; `…/workflow-actions`, `…/agent-activity` slot in later as siblings.
- **Frontend — `modules/reporting/` owns the data seam + future report pages.** One `reportingPort`
  (`getDashboard()` now, more later), per-report Zod schemas + projections, per-report hooks, and the dedicated
  report **pages** (`ui/workflow-actions/`, `ui/agent-activity/` …) as they come.
- **The landing dashboard stays in `modules/dashboard/`** and consumes `modules/reporting` (the
  `useDashboardReportQuery` hook + the report schema). "Dashboard = the home overview surface"; "reporting = the
  shared model + the dedicated report pages."
- **Charts are shared** (`shared/ui/charts/`) so every report surface reuses them, not dashboard-local.

## Scope decisions (resolved with the user during grooming)
- **D1 — Full reporting endpoint now (real).** A real `GET /admin/reporting/dashboard` returning KPIs + 24h
  time-series + funnel + outcomes + per-workflow + deltas. Aggregations are low-effort (well-indexed tables, no
  schema changes). The dashboard ships **fully real**.
- **D2 — Build the UI exactly as designed; stub the missing action endpoints.** Processed-call replay is real;
  **workflow-run replay has no backend** → ship a **stub** (`POST /admin/workflow-runs/{id}/replay`, 202, no
  re-execution, no event emission) so the worklist is fully wired. Any datum with no real source (per-run
  retries, "replayed today") is surfaced honestly / stubbed at the DTO — never fabricated.
- **D3 — Charts are hand-rolled SVG** in `shared/ui/charts/` (no chart lib; handoff agrees).
- **D4 — Reporting-module-first** (this section): the reporting model is the single source; the dashboard is
  surface #1; folders are structured for additive report types.

## Documents
- [`research.md`](./research.md) — design spec, the data ↔ backend reconciliation, the backend feasibility dig,
  the FE reuse inventory.
- [`plan.md`](./plan.md) — architecture, lifecycle diagram, phased implementation, file lists, verification,
  repo-decisions impact.
- [`phases.md`](./phases.md) — phase tracker (Part A backend reporting model · Part B dashboard surface).

## Out of scope (explicit)
- The future dedicated report pages (workflow-actions, agent-activity) — this feature only builds the module +
  the dashboard surface; later report pages are follow-up features that reuse this module.
- **Real workflow-run replay execution** (stub only here; needs an idempotency/dedup decision).
- **Webhook per-status pipeline tracking** — `webhook_events.status` is always `RECEIVED`; the funnel's
  "normalized" stage is derived from the `events` (domain-events) table via join.
- **Run-level retry history / "replayed today"** — runs track retries per *step*, not per run; stubbed/derived
  pending a decision.
