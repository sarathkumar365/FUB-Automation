# Reporting Platform — Phases

> Phase-by-phase build tracker. Detail per phase lands in `phase-<n>-implementation.md`
> as it's built. When every phase is complete, consolidate to the archived shape
> (`README.md` + `plan.md` + `implementation-log.md`).
>
> Plan: [`plan.md`](./plan.md). Architecture: [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md).

| Phase | Status | Summary |
|-------|--------|---------|
| 0 — Foundation (docs) | Done | Research/findings consolidated, RD-012 (Proposed), plan + this tracker, the "add a report" recipe. No code. |
| 1 — Dashboard metrics (direct slice) | **Built (2026-06-24, on `feature/reporting-platform`)** — [backend](./phase-1-backend-implementation.md) + [frontend](./phase-1-frontend-implementation.md) | Operational pipeline health, **all over one 24h window**: windowed run counts + success rate + deltas, hourly throughput, **operational** funnel `Ingested→Domain Events→Workflow Runs→Failed` (runs-only scope, **volume counts only — no conversion %s**), view-only failures worklist, recent runs, events/min, and a **3-state health headline** (HEALTHY/DEGRADED/UNHEALTHY) on success rate. **One read-only snapshot endpoint (`GET /admin/dashboard/snapshot`) + a dashboard-local JDBC read repo + a DTO — no framework.** BE (read repo+IT, snapshot service/controller) + FE (port/adapter/contract, full-width handoff UI w/ custom AreaChart+Sparkbars). Backend suite + UI gate green. **Known: board is static within the clock hour (floor-to-hour); rolling-window fix deferred.** Not yet merged to `dev`. |
| 2 — Timeline layer + two reports (semantic layer + deterministic reports, incl. accountability) | **Build-ready 2026-07-02** — spec: [phase-2-implementation.md](./phase-2-implementation.md); FE brief: [phase-2-design-handoff.md](./phase-2-design-handoff.md) ([scoping](./phase-2-accountability-scoping.md), [RD-014](../../repo-decisions/RD-014-reporting-query-architecture.md)) | **Timeline-first.** Base = a **lead-timeline layer** (on-read SQL views over `events`: per-lead history + **holder-intervals**), encoding [data-truths.md](./findings/data-truths.md). Then two reports: **R1 — Source → leads → contacted (any channel)** (coverage; snapshot flags; surfaces Facebook 51% vs Instagram 92%) and **R2 — Assigned → contacted, timeline-correct** (accountability; each contact credited to the **holder-at-the-time**, immune to reassignment). Windows **24h/7d, forward-only** (historical out of scope; continuous hosting makes short windows complete). Owner `uid=1` rendered as a normal agent. Admin/owner-facing; per-agent scoping later. Direct slice (plain SQL views, **not** the RD-012 framework; **no** materialized read-model — that stays Phase 3). |
| 2b — Ingestion reconcile/backfill job (OPTIONAL — no longer a prerequisite) | Not started — **demoted 2026-07-02** | A FUB `/v1/calls`+`/v1/people` since-last-sync reconcile/backfill. **No longer blocks Phase 2:** the app now runs continuously, so forward windows are complete (data-truths §2.1). This job only **buys back pre-hosting history** or **insures against deploy/restart blips**. Build on demand. |
| 3 — Extract platform framework | Not started | Extract `ReportProvider` / `ReportRegistry` / `ReportingQuery` port / `definitions` / generic controller **from the two working slices** (rule of three) — no behavior change to Phases 1–2. **Ratifies RD-012 → Accepted.** |
| 4 — Natural-language querying (DEFERRED · OPTIONAL · DEMAND-GATED) | Not started — **may never be built** | Per [RD-014](../../repo-decisions/RD-014-reporting-query-architecture.md): an "agent + MCP over the curated views + data-truths context," read-only, **labelled exploratory, never the source of an accountability number**. Build only if real demand for *unanticipated* questions appears (RD-014 criteria a/b/c). Filterable deterministic reports cover most apparent "ad-hoc" need without it. |

> **Sequencing note (2026-06-19 stress-test).** The framework is built **last**, extracted
> from two real reports — not first. Committing the abstraction before two working examples
> would lock in the contract at the moment of least information. See plan.md "Order of work".
> **The semantic-layer views (Phase 2) are plain SQL, not the framework** — building them now does
> not violate "extract last" (RD-014 §Relationship to RD-012).

**Deferred beyond these phases (via RD-012 seams, not in this pass):** FUB CDC mirror →
task-completion (Q5/Q6) + "useful" (Q4) + deeper funnel; **NL/agent querying (Phase 4 — demand-gated,
RD-014)**; FUB-user ingestion; text/SMS contact; FUB intake date.
