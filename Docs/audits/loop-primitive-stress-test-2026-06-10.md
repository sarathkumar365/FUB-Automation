# Loop Primitive Plan — Adversarial Stress-Test Audit

**Date:** 2026-06-10
**Target:** `Docs/features/workflow-engine/loop-primitive/` (plan.md, README.md), `Docs/features/workflow-engine/steps/loop.md`, RD-009, RD-010 — on branch `feature/loop-primitive` (design docs + Phase 0 shipped; Phases 1–6 not started)
**Method:** 4 independent hostile reviewer agents with zero design-conversation context, each attacking from a distinct angle; 24 named attack iterations total. Attackers read the docs *and* the engine source; every claim verified at file:line. Local dev DB sampled for real row sizes (labeled extrapolations where used).

---

## Overall verdict

**The architecture survived; the plan did not.** No attacker found a reason the foreman + inline-cloned-lap-rows model is wrong — every critical finding lives in seams the plan covers with a single sentence ("small wake-hook", "reused unchanged", "swallow the violation"). All fixes are amendments, not redesigns — but several are mandatory before Phase 1/2 code, and one (the supersede interaction) is a product decision without which the MVP use case fails in production.

---

## Scoreboard — 24 attack iterations

| # | Attack | Verdict | Sev |
|---|---|---|---|
| A1 | Fan-out lap-end race (two tails finish simultaneously, 2 instances) | 🔴 BREAKS — under READ COMMITTED each tx sees the other's PROCESSING row → *neither* pokes → loop stalls to fallback | HIGH |
| A2 | Poke vs foreman re-sleep race | 🔴 BREAKS — `scheduleReschedule` does a blind read-modify-write of `due_at` (no `@Version`); re-sleep overwrites a concurrent poke → lost wake | HIGH |
| A3a | "Swallow the UNIQUE violation" idempotency | 🔴 CRITICAL — on Postgres a unique violation **aborts the tx**; as written the violation propagates, worker compensation FAILs the run on a benign duplicate stamp | CRIT |
| A3b | `stale_recovery_count` lifetime fuse | 🔴 CRITICAL — counter never resets, default requeue limit **1**; a months-lived foreman claimed hundreds of times is near-certain to be force-FAILED by the 2nd unlucky deploy/crash | CRIT |
| A3c | Run-killing paths bypass lap-failure catch | 🔴 CRITICAL — `markStepAndRunFailed`, worker crash compensation, stale recovery all fail the run directly, never via transitions; S14 false by construction | CRIT |
| A4a | `checkRunCompletion` vs sleeping foreman | 🟢 HOLDS — PENDING is non-terminal (`WorkflowStepExecutionService.java:403-414`); run cannot finalize under a sleeping foreman | — |
| A4b | `anyFailed` flips caught-failure runs | 🔴 BREAKS — any lap row left FAILED makes run finalize FAILED even after a successful `onLapFailure: CONTINUE` loop | HIGH |
| A4c | Parallel main-flow branch hits terminal mid-lap | 🟠 UNDERSPECIFIED — sweep SKIPs the foreman; in-flight lap side effects still fire after run COMPLETED | MED |
| A5 | Phantom re-poke / lap-number confusion | 🟠 UNDERSPECIFIED — slow-but-alive tail past 15-min stale timeout executes twice; safety depends on a wake algorithm the plan never writes down | HIGH |
| A6 | Poke implementation under 2 instances | 🔴 CRITICAL ceiling — the codebase-default JPA load+save poke would clobber the foreman's notebook (no `@Version`, full-column writes); must be a guarded JDBC UPDATE | CRIT |
| B1 | Untaken-branch rows vs lap-end COUNT | 🔴 CRITICAL — branch leaves sibling path `WAITING_DEPENDENCY` forever: counted → poke never fires (hang); not counted → orphan rows block run completion | CRIT |
| B2 | Mode-dependent exit wiring | 🔴 BREAKS — `while` + unwired `ZERO_LAPS` → `TRANSITION_NOT_DEFINED` → healthy zero-lap run FAILED; validator never requires declared codes to be wired (`WorkflowGraphValidator.java:144-171`) | HIGH |
| B3 | forEach items frozen vs live `person.*` | 🟠 UNDERSPECIFIED — items list must be pinned in the notebook or crash-recovery re-evaluates a changed list and the cursor indexes a different array | MED→HIGH |
| B4 | Nested-loop relation-column collision | 🔴 BREAKS — `parent_loop_node_id='inner'` collides across outer laps (`call#2#1` vs `call#3#1` both parent='inner', lap=1); lap-end COUNT and `lastLap` reads merge rows from different outer laps. **Only finding needing a migration if caught late** | HIGH |
| B5a | Dead end vs forgotten result code | 🟠 UNDERSPECIFIED — plan silently redefines "missing transition" from run-failing error (`applyTransition:309-312`) to normal lap end; forgotten codes become silent early lap ends | HIGH |
| B5b | Loop as entryNode / lap-1 delay | 🟠 UNDERSPECIFIED — `resolveEntryDueAt` reads `delayMinutes` (loop has `lapDelayMinutes`); lap-1 delay semantics never stated | LOW-MED |
| B5c | Templated `delayMinutes` in bodies | 🔴 BREAKS (silently) — `resolveStepDueAt:377-392` reads raw config, `instanceof Number` fails on template strings → delay silently 0; loops industrialize this pre-existing footgun (backoff use case) | MED-HIGH |
| B6 | Condition scope contradiction + synthetic-id leakage | 🔴 BREAKS — loop.md's own sketch uses `steps.wait_check.resultCode`, which its own Semantics section excludes and the engine has never provided (`ExpressionScope.java:43-51` exposes outputs only); `buildRunContext:211-218` would leak every `call#N` into all later steps' scope, inverting lean-outputs decision #10; `steps.call#3` is a JSONata parse error degrading to silent null | HIGH |
| C1 | RunSupersedePolicy × loops | 🔴 **CRITICAL (worst finding)** — mid-loop run is PENDING for life → supersede candidate; MVP triggers on `assignedUserId.changed`, so trigger-match ⇒ field-overlap: **every reassignment cancels the multi-day loop and resets the lap budget**. Daily-reassigned leads never escalate; churned leads can get k×N nudges. Product semantics undecided | CRIT |
| C2 | RD-006 echo amplification | 🟢 HOLDS for MVP — echo gate applied before plan(); caveats: in-memory tracker TTL 30s + restart windows mislabel echoes; workflows whose body writes their own trigger field can self-oscillate | MED |
| C3 | Row/snapshot bloat | 🟠 HIGH — no retention/purge code exists anywhere; `buildRunContext` loads ALL run rows per step (O(N²): forEach 200×5 ≈ 500k row hydrations in one run on one thread); nested ceilings multiply (100×100×3 = 30k rows is validator-legal); run-detail endpoint unpaginated (`WorkflowRunQueryService:107`) | HIGH |
| C4 | Poller starvation | 🔴 HIGH — **one scheduler thread** (no TaskScheduler config → Spring default 1); FUB RestClient built with **no timeouts** (`HttpClientConfig:12`); one hung call freezes the engine *including its own stale recovery*; 500-foreman burst ⇒ ~5–7 min lap latency vs the 2s promise | HIGH |
| C5 | Observability | 🟠 HIGH — zero metrics infra in repo (no actuator/micrometer in pom); plan's "alert on foreman age" has no phase owner; fallback self-heal masks systemic poke regressions | HIGH |
| C6 | RD-007/RD-010 extraction coupling | 🟢 HOLDS — kernel boundaries clean; trip-wires: Phase 6 DTO work would enlarge the known L1 layering leak; Phase 1 validator recursion must not hard-wire the `PersonUpsertService` leak deeper | LOW-MED |
| D1 | Internal contradictions (6 found) | 🔴 HIGH — dead-end def (D1.1); RD-009 §4 "separate transactions" vs S3 "same transaction" poke (D1.2); `LOOP_FAILED` routing impossible as described — failure-typed results never route through transitions (D1.3); `lapDelayMinutes` unpinned at 3 edges (D1.4); "(+ fixed config-failure codes)" names codes that exist nowhere (D1.5); body-local id uniqueness incompatible with suffix-strip-only resolution — two sibling loops can both own a body node `call` (D1.6); foreman status "PENDING/PROCESSING" is a deferred decision wearing a slash (D1.7) | HIGH |
| D2 | Phase-boundary realness | 🟠 "Paper" at the load-bearing seams — Phase 1 validator rules undecidable without Phase 2/3 semantics (circular review dependency) | HIGH |
| D3 | Estimate audit | 🟠 Realistic 25–35d (not 20–27); kill-restart harness unbudgeted and secretly due in Phase 3 (its own done-signal requires restart tests); conformance fixture factory unbudgeted | HIGH |
| D4 | Missing-work sweep | 🔴 UI builder is **flat-list by design** (`ui/src/modules/workflows-builder/model/graphAdapters.ts:5`) — nested bodies break adapter/layout/validation (1–2 wks or an explicit JSON-only-v1 decision); **no kill-switch** (only lever is `workflow.worker.enabled` = kill everything); `create-workflow-json` skill needs loop guidance; 4 repo doc rules violated (no Mermaid lifecycle diagram, no `phases.md`, no `research.md`, phase-0 doc missing repo-decisions-impact section) | HIGH |
| D5 | S1–S17 testability | 🟠 S3 (tx-boundary assert), S4 (poke-suppression hook in prod code), S10 (kill-restart harness ×3) need infrastructure no phase owns — hidden scope concentrated in Phase 3 | HIGH |
| D6 | Doc drift | 🟡 loop.md written ahead of code in a repo where written specs are binding; 5-phase drift window with no owner until Phase 6 | MED-HIGH |

---

## Top findings (ranked, with mechanisms)

### 1. Supersede × loop semantics (C1) — product decision, CRITICAL
A looping run sits in `workflow_runs.status = PENDING` for its entire life (there is no RUNNING status), making it a supersede candidate throughout. `RunSupersedePolicy` cancels a PENDING run of the same workflow+person when `changed_fields` overlap — and for the MVP (trigger: `assignedUserId.changed`) any event that plans a new run *necessarily* overlaps. Net effect: every reassignment cancels the in-flight enforcement loop and restarts at lap 1. Consequences: leads reassigned daily **never reach escalation** (the loop's whole purpose); leads reassigned k times can receive up to k×N nudges. The plan's risk table only covers the *mechanics* of cancel (orphan-row sweep) — the *semantics* (restart vs keep vs carry budget) is an undecided product behavior shipping by accident. **Decision required — see Decisions section.**

### 2. The concurrency seams are one sentence of design (A1+A2+A3a+A6)
- **Poke mechanism unspecified.** The codebase-default implementation (JPA load+save) would write *every column* of the foreman row from a stale snapshot — `WorkflowRunStepEntity` has no `@Version` — silently rolling the notebook back a lap. Must be specified as a single guarded JDBC `UPDATE … SET due_at = now WHERE id = :foreman AND status = 'PENDING'`.
- **Lost wakeups.** `scheduleReschedule` (`WorkflowStepExecutionService.java:530-539`) blindly overwrites `due_at`; a poke landing between the foreman's read and its re-sleep write is obliterated, and since pokes fire once per lap-end, the loop stalls until fallback. Needs a min-wins/conditional-update rule.
- **Fan-out lap-end race.** Two tails completing in parallel transactions each see the other's committed PROCESSING row → both count >0 → **neither pokes**. Fix: poke unconditionally on every body-tail completion (idempotent) and let the foreman do the authoritative count, or lock the foreman row before counting.
- **Unique-violation "swallow" is unimplementable as written.** Postgres aborts the transaction on constraint violation; the swallow needs a pre-check SELECT or a savepoint, and without it the generic failure path FAILs the run on a benign duplicate stamp.

### 3. Run-killing paths bypass the lap-failure catch (A3c+A4b+A3b)
Three paths fail the run without consulting transitions: `markStepAndRunFailed` (`:443-456`), worker crash compensation (`markClaimedStepFailedAfterWorkerException:172-178`), stale recovery (`applyStaleProcessingRecovery:199-208`). All must become lap-aware (route body-row failures to a foreman wake). Additionally `checkRunCompletion` sets `anyFailed` on any FAILED row (`:408-417`) — so even a *successfully caught* lap failure flips the run FAILED at loop exit; caught laps need a non-FAILED terminal status (or exclusion by `parent_loop_step_id`). And `stale_recovery_count` (default limit 1, never reset) will eventually kill any long-lived foreman — needs per-claim-cycle reset or exemption policy.

### 4. Branching bodies break lap-end mechanics (B1 + B5a)
A body branch leaves the untaken path's stamped rows `WAITING_DEPENDENCY` forever. If "non-finished" includes them: the lap-end COUNT never reaches zero → poke never fires → permanent stall (S6 is unsatisfiable). If it excludes them: orphan rows accumulate and block `checkRunCompletion` forever. Fix (cheap, must be written before Phase 2): at lap end, sweep still-WAITING lap rows to SKIPPED; define "non-finished" = PENDING|PROCESSING|WAITING_DEPENDENCY *after* the sweep. Related: "any dead end ends the lap" silently converts today's loud `TRANSITION_NOT_DEFINED` error into a silent early lap end for forgotten result codes — the contract must distinguish *declared* dead ends (explicit empty `transitions: {}`) from unmatched codes (still an error, routed to lap-failure).

### 5. The nesting schema is wrong as specified (B4 + D1.6)
`parent_loop_node_id` keyed on the loop *node id* collides across outer laps: outer lap 2's inner-lap-1 rows and outer lap 3's are indistinguishable (`(run, 'inner', 1)` matches both) — lap-end COUNTs and `lastLap` reads merge rows from different outer laps. Fix now for free: key on the foreman **instance** — `parent_loop_step_id BIGINT` FK to the foreman's own row (or instance node_id `inner#2`). Also: Phase 1's "body-local id uniqueness" is incompatible with Phase 2's "suffix strip at `findNodeInGraph`" — two sibling loops can both own a body node `call`, and the resolver has only the id. Decide: global uniqueness, path-encoding suffixes, or resolution via the relation columns.

### 6. Condition scope must be pinned (B6 + B2 + B3)
loop.md's own example (`steps.wait_check.resultCode`) violates its own Semantics section and references a scope shape (`resultCode`) the engine has never exposed. Decide the condition dialect (recommended: `lap`, `item`/`index`, `lastLap.<bodyNodeId>.{resultCode, outputs.*}`, `person/now/event`, **no `steps.*`**), have the validator reject `steps.` in loop conditions, exclude lap rows from `buildRunContext`'s steps scope (one WHERE clause via the relation columns), define `lastLap` for nodes that didn't run this lap (absent), pin the forEach items array into the notebook at loop start, and add mode-dependent mandatory exit wiring (`while`/`forEach` must wire `ZERO_LAPS`; all modes must wire `LOOP_FAILED`).

### 7. Ops reality (C4 + C5 + C3 + D4g)
One scheduler thread; FUB client without timeouts (one hung call freezes everything including recovery); zero metrics infrastructure; no retention story; no kill-switch. Loops multiply all exposures ~5–7×. Minimum adds: FUB client timeouts (pre-existing fix, weaponized by loops); bounded executor for claimed-step execution; `workflow.loop.enabled` kill-switch; a metrics work item (foreman fallback-wake counter = the smoking gun, wake latency, oldest-foreman age, exit-reason counters); a total-materialized-rows-per-run validator ceiling; lap-scoped context loading; run-detail pagination; a retention decision.

### 8. Delivery risk (D2 + D3 + D5 + D4 + D6)
Phase boundaries are paper at the contract seams — fix with a **Phase 1 normative contract** (one page: dead-end rule, id-scope + resolution, exit-code typing, scope dialect, lapDelay edges, foreman status = PENDING) reviewed before validator code. Test infra needs explicit line items (kill-restart harness — due in *Phase 3* per its own done-signal; poke-suppression hook; tx-boundary asserts; conformance fixture factory). UI builder: decide JSON-only v1 or budget 1–2 weeks. Estimate re-baselines to ~25–35 days. Repo-rule compliance: add the Mermaid lifecycle diagram, `phases.md`, `research.md`, and the phase-0 repo-decisions-impact section. Doc-drift guard: every phase's done-signal includes "loop.md sections affected updated or marked superseded."

---

## What held ✅ (fairness section)

- **Foreman-sleeps ⇒ run-not-finalized** — verified true in code (`checkRunCompletion` treats PENDING as non-terminal).
- **RD-006 engine-echo gating** — holds for the MVP; gate applied before plan(), loop path doesn't bypass it.
- **Claim-query scalability** — `(status, due_at)` partial-prefix scan unaffected by millions of terminal rows; structural HOLD.
- **RD-007/RD-010 kernel boundaries** — LoopWorkflowStep as CONTROL stays import-clean; relation columns live on kernel tables; Phase 0 boundary test enforces.
- **Workflow versioning/rollback with nested bodies** — graph JSON copied whole; non-issue (one round-trip test suffices).
- **Snapshot deep-nesting** — `validatePersonFieldReferences` already recurses into config maps; body configs get person-field validation for free.
- **The architecture itself** — no attacker proposed a better one; every fix is an amendment.

## Pre-existing engine issues amplified (not plan bugs — separate owners)

| Issue | Amplification |
|---|---|
| FUB RestClient has no timeouts (`HttpClientConfig:12`) | One hung call freezes the single worker thread incl. stale recovery; loops 5–7× the call volume |
| Single scheduled worker thread, serial batch | Lap latency under burst; foremen queue behind slow BUSINESS steps |
| Templated `delayMinutes` silently 0 (`resolveStepDueAt` reads raw config) | Loops industrialize the backoff use case |
| `buildRunContext` loads all run rows per step | O(N²) with lap-row multiplication |
| In-memory engine-write tracker (TTL 30s, lost on restart) | Multi-day loops make deploy-window echo mislabels routine |
| `stale_recovery_count` never resets (limit 1) | Lethal to any months-lived row; loops create the first such rows |
| Run-detail endpoint loads all steps unpaginated | Lap rows inflate the DTO |

## Decisions only the owner can make

1. **Supersede × loop** — when a new trigger event lands on a person with an in-flight loop: (a) keep the loop, ignore the event; (b) restart (today's accidental behavior — fresh lap budget); (c) per-workflow config `onSupersede: KEEP|RESTART`; (d) carry lap/nudge budget across runs (needs a cross-run counter that doesn't exist). Note: for the MVP specifically, restart-on-*reassignment* is arguably correct product behavior (new agent ⇒ fresh enforcement) — the trap is budget-reset abuse and never-escalation under churn.
2. **v1 authoring surface** — JSON-only (builder renders loops read-only/later) vs budget 1–2 weeks of builder work for nested bodies.
3. **Estimate re-baseline** — accept ~25–35 days (with the new line items) vs descope (e.g., defer nesting from v1 to cut the S-matrix and harness scope).

## Proposed remediation (summary — to be applied as plan amendments)

- **Phase 1:** add the normative-contract deliverable; mode-dependent exit-wiring rule; condition-dialect validation; total-rows ceiling; `delayMinutes`-in-body rules.
- **Phase 2:** concurrency appendix (poke = guarded JDBC UPDATE; unconditional poke + foreman-side authoritative count; min-wins due_at rule; stamp+notebook+reschedule in one tx with pre-check idempotency; stale-counter policy); schema: `parent_loop_step_id` FK (instance, not node id); lap-end sweep of untaken-branch rows; `buildRunContext` lap-row exclusion.
- **Phase 3:** lap-aware failure paths (3 bypasses); caught-lap status; `workflow.loop.enabled` kill-switch; foreman status pinned PENDING; wake algorithm written as a numbered procedure; test-infra line items (kill-restart harness, poke-suppression hook, tx-boundary asserts).
- **Phase 4:** pin items array into notebook; document frozen-items vs live-person split.
- **Phase 5:** + S18 (sibling-branch terminal mid-lap), S19 (late duplicate poke from stale-requeued tail), S20 (foreman-internal exception → LOOP_FAILED); conformance fixture factory as a sized item.
- **New Phase 5.5 (or extend 6):** metrics + alerts (requires actuator/micrometer adoption decision); foreman fallback-wake counter, wake latency, oldest-foreman age, exit-reason counters.
- **Phase 6:** land L1 DTO relocation first or group laps client-side; finalize loop.md.
- **Docs hygiene:** Mermaid lifecycle diagram in plan.md; `phases.md`; `research.md` (capture the five-mechanism analysis); phase-0 repo-decisions-impact section; per-phase loop.md drift-guard.
- **Separate (pre-existing, not loop-owned):** FUB client timeouts; bounded step-execution executor; retention/archival strategy; run-detail pagination.
