# Reporting — architecture direction (learnings)

> **Status: historical brainstorm — retained for provenance.** The **current source of truth** is
> **[RD-014](../../repo-decisions/RD-014-reporting-query-architecture.md)** + the **2026-07-02 audit**
> ([data-truths.md §2.1 CORRECTION](./findings/data-truths.md)). Two notes for the reader:
> - **Superseded here:** the specific "mirror read-model + Vanna text-to-SQL" shape below is sharpened by
>   RD-014 into "on-read SQL views now, agent-over-MCP NL deferred." Read RD-014 for the live shape.
> - **Re-vindicated here:** this doc's core thesis — *"a mirror + reconcile is the capture spine, not
>   optional"* — was **proven right** by the 2026-07-02 audit (the webhook ingress drops ~59% of calls;
>   the reconcile job is now mandatory, Phase 2c). The "rejected: reporting-as-a-live-agent" reasoning
>   and the "known limits" list also still stand.

## The settled shape

```
FUB ──webhooks + reconcile──▶ [mirror tables] ──projection──▶ [reporting read model]
                                                                      │
                                          ┌───────────────────────────┴──────────────┐
                                   deterministic SQL                          text-to-SQL (Vanna)
                                   (dashboard, fixed metrics)                 over the SAME read model
                                                                             (ad-hoc NL questions)
```

Two query subsystems over **one** read model:

1. **Deterministic, hand-written SQL** — backs the operations dashboard and any fixed,
   named metric. Fast, reproducible, reviewed once, frozen in code.
2. **Text-to-SQL (Vanna AI, separate Python service)** — the natural-language interface
   for ad-hoc user questions. Queries the **same read model**, never FUB live, never the
   source tables freehand.

## Why a mirror is the spine (not optional)

The questions we care about are **mostly historical** (on-time completion was explicitly
deprioritised — see below). Historical means: *if we didn't record it, it's gone.* The
past is not fetchable from FUB on demand. Therefore a faithful, accumulating mirror of
FUB state (tasks, later appointments/deals) via **webhooks + periodic reconciliation** is
the **only** thing that makes task-completion / lifecycle questions answerable at all.

This is the capture spine. Everything else (dashboard SQL, text-to-SQL) reads from it.

## Rejected: reporting-as-a-live-agent

Idea floated: make reporting a full agent that reasons and acts at query time — executes
SQL, calls the FUB API live to fetch what it needs (e.g. for Q6, pull the agent's tasks
for the last 10 days straight from FUB).

**Rejected for v1.** The agent is the right tool in the wrong layer — it's a *query
interface*, not a *source of truth*. Relocating the fetch to query-time doesn't create
data that wasn't captured. Hard problems:

- **Live fetch can't answer historical questions.** No API call for the past; no
  point-in-time truth. FUB state is mutable (reopened / edited / reassigned), so a
  snapshot taken today ≠ what was true when it mattered.
- **Can't back a dashboard.** Per-load LLM reasoning + paginated FUB calls = seconds, not
  sub-second; and it burns tokens per load.
- **FUB rate limits become the ceiling.** Every query re-pulls from FUB; a mirror pulls
  once and serves infinitely.
- **Non-deterministic numbers on an accountability product.** Same question can yield
  different SQL → different numbers. Indefensible when an agent contests "you said I
  didn't follow up."
- **Definition traps re-derived per query.** "Contacted" / "followed up" are landmines
  (Run 163). They must be frozen in reviewed SQL, not re-decided by a model each call.
- **Security blast radius.** An autonomous agent holding live SQL + a FUB credential is a
  large roaming surface — worse than the read-only / allowlist text-to-SQL posture.

**What survives of the agent idea:** the LLM stays, but only as the **natural-language
interface over the read model** (the Vanna subsystem) — never a live fetcher, never
computing accountability numbers itself.

**Where live fetch would have been legitimate:** a narrow single-entity "is *this* task
done right now" lookup. With on-time/real-time dropped, that niche is empty in v1, so live
FUB calls are out entirely — not deprioritised, out.

**Bolder reframe worth keeping in mind:** use the LLM at **build time, not run time** —
let it *author* metric SQL once (Vanna's trained mode generates SQL you review before it
ships). AI writes it, humans own it, runtime stays deterministic and fast.

## Scope-narrowing decisions from this session

- **On-time completion deprioritised.** Q6 simplifies from "finished *on time*?" to
  "ever completed?" — a flag on the mirrored task. This removes the scheduled per-task
  "check at the due moment" job originally proposed; the mirror's completion flag,
  refreshed on reconciliation, answers Q5 and Q6 both.
- **Mostly historical, not real-time.** Drives every decision above toward capture-and-store
  over fetch-on-demand.

## Data-availability recap (from the data-source deep-dive)

- **Answerable today:** assigned → contacted — from the `events` diary (assignment
  history) + `processed_calls` (contact). See Q2/Q3 in [plan.md](./plan.md).
- **Blocked today — needs the mirror:** task completion (Q5/Q6), "useful" outcome (Q4,
  candidate signals = FUB `appointmentsCreated` / `dealsCreated`). Today we only capture
  **engine-created** tasks (in `workflow_run_steps.outputs` JSON), not human-created ones.

## Known limits & open questions (stress-test, 2026-06-19)

A fresh-eyes stress-test of the foundation surfaced these. **The single change adopted** was
sequencing — build the MVP + dashboard as direct vertical slices and *extract* the framework
from them (rule of three) rather than committing RD-012 up front (now reflected in plan.md /
phases.md / RD-012). The rest are **tracked as known limits, deliberately not yet solved**:

- **The real ceiling on "all asks" is DATA, not the framework.** Only calls / persons /
  notes are ingested. Tasks, appointments, deals, texts, emails, agent metadata,
  source→closing are all absent — so most *interesting* reporting is blocked on the mirror
  regardless of how good the read layer is. The mirror is the precondition for breadth, not
  a late nice-to-have.
- **Multi-tenancy is unaddressed.** If the platform ever serves more than one brokerage,
  tenant scoping is a cross-cutting concern that's painful to retrofit and a data-leak risk.
  *Open question for the owner: single-team or multi-tenant?* Decide before the framework is
  extracted (it changes the port + definitions contracts).
- **Pull-only architecture.** Alerts, scheduled digests, "notify me when…" are *push* —
  scheduler + threshold + notification channel, none of which the current design has. If
  proactive reporting is in scope, it's a separate seam to design, not a provider.
- **No caching / materialization strategy.** Every report is live SQL over operational
  tables, including event-replay aggregations. Fine at low volume; a wall as history grows.
  Materialized views / cache / read replica for the reporting path are unplanned.
- **Definitions likely need parameterization, not constants.** "Contacted = connected-only"
  vs. "= all attempts" vs. "incl. texts" are different asks against one metric. The shared
  `definitions` module should be designed to take parameters, or it breaks on the first
  nuanced request.
- **No report-correctness / reconciliation strategy.** Wrong accountability numbers are
  worse than none (false accusations). A golden-dataset / reconcile-against-FUB check should
  accompany the accountability slice.

## Charting — custom for primitives, library for analytics (RD-013)

Reporting charts split in two. **Decorative design-primitives** (the dashboard throughput
line, funnel sparkbars — no axes, scales, or interactivity) are **custom token-driven SVG,
no library** — a port of the handoff's `charts.jsx`. The later **analytical layer**
(axes / multi-series / interactivity; the ad-hoc / Vanna views) adopts a **library, chosen at
that point** from real requirements — **lean visx** (composes with the custom charts and the
token system; the custom charts port into it, not throwaway). Avoid batteries-included
declarative libs (Recharts/Tremor). **Don't pick the library off the dashboard** — it's an
atypical decorative consumer; evidence-first, per RD-012. See
[RD-013](../../repo-decisions/RD-013-reporting-charts-custom-vs-library.md).

## Open questions (parked — design detail)

- **Does FUB's task webhook + incremental pull carry a trustworthy completion flag/state?**
  This single fact decides whether Q5/Q6 are buildable as described. To verify against
  current FUB API docs at planning time.
- CQRS read-model grain; projection timing; backfill strategy.
- Text-to-SQL security posture (read-only role, SELECT allowlist, SQL validation, audit) —
  flagged as RD-worthy.
- Layered placement of the reporting package and per-module API surface (brainstormed; not
  yet recorded as decisions).
