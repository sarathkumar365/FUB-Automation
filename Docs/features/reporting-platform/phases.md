# Reporting Platform — Phases

> Phase-by-phase build tracker. Detail per phase lands in `phase-<n>-implementation.md`
> as it's built. When every phase is complete, consolidate to the archived shape
> (`README.md` + `plan.md` + `implementation-log.md`).
>
> Plan: [`plan.md`](./plan.md). Architecture: [RD-012](../../repo-decisions/RD-012-reporting-platform-architecture.md).

| Phase | Status | Summary |
|-------|--------|---------|
| 0 — Foundation (docs) | In progress | Research/findings consolidated, RD-012, plan + this tracker, the "add a report" recipe. No code. |
| 1 — Platform skeleton | Not started | `ReportProvider`, `ReportRegistry` (auto-discovery), `ReportingQuery` port + deterministic-SQL adapter, `definitions`, generic controller. Proven by one trivial provider end-to-end. Ratifies RD-012 → Accepted. |
| 2 — Accountability MVP | Not started | Assigned → called, red/green per agent. Coverage + attribution layers; contains the `source_user_id` null-rate gate. |
| 3 — Dashboard metrics | Not started | Existing-data dashboard capabilities (windowed counts, hourly series, deltas, leads→assigned→called funnel, failures worklist, recent runs, live readout). Reuses Phase-1 foundation + Phase-2 definitions. |

**Deferred beyond these phases (via RD-012 seams, not in this pass):** FUB CDC mirror →
task-completion (Q5/Q6) + "useful" (Q4) + deeper funnel; Vanna text-to-SQL ad-hoc
questions (+ its security RD); FUB-user ingestion; text/SMS contact; FUB intake date.
