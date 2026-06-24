# Reporting Platform — Phases

> Phase-by-phase build tracker. Detail per phase lands in `phase-<n>-implementation.md`
> as it's built. When every phase is complete, consolidate to the archived shape
> (`README.md` + `plan.md` + `implementation-log.md`).
>
> Plan: [`plan.md`](./plan.md). Architecture: [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md).

| Phase | Status | Summary |
|-------|--------|---------|
| 0 — Foundation (docs) | Done | Research/findings consolidated, RD-012 (Proposed), plan + this tracker, the "add a report" recipe. No code. |
| 1 — Dashboard metrics (direct slice) | Planned — **BE first** [backend](./phase-1-backend-implementation.md) → then [frontend](./phase-1-frontend-implementation.md) (spec rev 4, 2026-06-24) | Operational pipeline health, **all over one 24h window**: windowed run counts + success rate + deltas, hourly throughput, **operational** funnel `Ingested→Domain Events→Workflow Runs→Failed` (runs-only scope, **volume counts only — no conversion %s**), view-only failures worklist, recent runs, events/min, and a **3-state health headline** (HEALTHY/DEGRADED/UNHEALTHY) on success rate. **One read-only snapshot endpoint + a dashboard-local JDBC read repo + a DTO — no framework.** Built as two sub-slices: backend (endpoint + contract) then frontend (consumes it). |
| 2 — Accountability MVP (direct slice) | Not started | Assigned → called, red/green per agent. Second direct slice, same plain style. Coverage + attribution layers; contains the `source_user_id` null-rate gate. |
| 3 — Extract platform framework | Not started | Extract `ReportProvider` / `ReportRegistry` / `ReportingQuery` port / `definitions` / generic controller **from the two working slices** (rule of three) — no behavior change to Phases 1–2. **Ratifies RD-012 → Accepted.** |

> **Sequencing note (2026-06-19 stress-test).** The framework is built **last**, extracted
> from two real reports — not first. Committing the abstraction before two working examples
> would lock in the contract at the moment of least information. See plan.md "Order of work".

**Deferred beyond these phases (via RD-012 seams, not in this pass):** FUB CDC mirror →
task-completion (Q5/Q6) + "useful" (Q4) + deeper funnel; Vanna text-to-SQL ad-hoc
questions (+ its security RD); FUB-user ingestion; text/SMS contact; FUB intake date.
