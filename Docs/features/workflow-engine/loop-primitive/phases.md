# Loop Primitive — Phase Tracker

> **⏸️ SHELVED 2026-06-10** — resume via [resume-briefing.md](resume-briefing.md)
> (apply its rev 3 patch list to plan.md before building Phase 1).
> Plan: [plan.md](plan.md). Decision sheet: [README.md](README.md).

| Phase | Scope | Status |
|---|---|---|
| 0 | Step-type categories (CONTROL/UTILITY/BUSINESS + CI boundary test, RD-010) | ✅ COMPLETED — [notes](phase-0-implementation.md) |
| 1 | Normative graph contract + validator (nested body, modes, wiring rules, ceilings, nesting rejected v1) | NOT STARTED |
| 2 | Engine substrate (V24 migration `parent_loop_step_id`/`lap_number`, suffix resolution, concurrency appendix: poke/min-wins/pre-check/stale-reset, scope exclusion) | NOT STARTED |
| 3 | Foreman v1 `until`/`while` (wake algorithm, lap-aware failure paths + `LAP_FAILED`, `onSupersede`, kill-switch, crash-state test harness) | NOT STARTED |
| 4 | `forEach` sequential (pinned items, item/index scope, empty list) | NOT STARTED |
| 5 | Conformance suite + scenario suite S1–S20 | NOT STARTED |
| 5.5 | Metrics & alerts (actuator/micrometer, foreman fallback-wake + age + supersede-restart metrics) | NOT STARTED |
| 6 | Run-detail lap grouping (client-side), loop.md finalization, create-workflow-json update, engine-doc lifecycle section | NOT STARTED |

**Re-baselined estimate (2026-06-10, nesting + builder deferred): ~20–27 dev-days.**

History: 2026-06-10 — plan rev 2 after the
[stress-test audit](../../../audits/loop-primitive-stress-test-2026-06-10.md)
(nesting deferred, supersede decided, schema instance-keyed, concurrency
appendix added).
