# Loop Primitive — Resume Briefing (read this first when picking the work back up)

> **Status: SHELVED 2026-06-10** — deliberately, not abandoned.
> **Why shelved:** the MVP's loop body is a *single step* (`wait_and_check_communication`
> — the "call" is the human agent), which the engine's existing
> reschedule+`step_state` pattern covers in ~40 lines with zero engine change
> (Path A, the `ai_call` pattern). Building the full primitive before a second
> real use case existed was judged over-engineering.
> **Resume trigger:** the first real workflow that needs to repeat a
> **multi-step group** (e.g. send SMS → wait → check, as one repeated unit), or
> a real `forEach` need.
> **What's NOT wasted:** Phase 0 (step categories + CI boundary test) shipped
> and serves engine-extraction regardless. All design decisions, traps, and
> fixes below are bought knowledge.

## State of the work when shelved

| Artifact | State |
|---|---|
| [README.md](README.md) | Decision sheet #1–15, all owner-decided |
| [plan.md](plan.md) | **rev 2** — phases 0–6 + normative contract + concurrency appendix. ⚠️ rev 2 contains 2 known spec bugs (below) — apply the rev 3 patch list before building |
| [phases.md](phases.md) | Phase 0 ✅ shipped (722 tests green); 1–6 not started |
| [loop.md](../steps/loop.md) | Step contract incl. v1/deferred capability ledger |
| [RD-009](../../../repo-decisions/RD-009-loop-primitive-foreman-cloned-rows.md) / [RD-010](../../../repo-decisions/RD-010-step-type-categories.md) | Architecture + categories decisions |
| [Stress-test audit](../../../audits/loop-primitive-stress-test-2026-06-10.md) | 24 adversarial iterations vs plan rev 1 → produced rev 2 |
| Readiness check (2026-06-10, 2 reviewers, summarized **below** — exists nowhere else) | Found the items in this file |
| [research.md](research.md) | Five mechanisms compared; industry survey |

**Verdict when shelved:** architecture sound (foreman + inline cloned lap rows
+ relation columns); plan NOT build-ready — the items below must be resolved
first. Estimated build after fixes: ~20–27 dev-days (nesting + builder deferred).

---

## A. Two spec bugs INSIDE plan rev 2 (fix the plan before building anything)

**A1. Wake-algorithm ordering bug (concurrency appendix, "Foreman wake algorithm").**
As numbered, the foreman *sweeps leftover WAITING rows → then counts*. On an
early/spurious wake (explicitly allowed by design, S5) this destroys a lap
that is still running, then rolls over. **Correct order: authoritative count
first → only if lap confirmed ended → sweep untaken-branch rows → decide.**
"Unfinished" for the count = PENDING|PROCESSING (WAITING rows belonging to
untaken branches are identified and swept only after lap-end is confirmed).

**A2. Actuator security premise inverted (Phase 5.5).**
Plan says actuator endpoints "are 401'd by current config". Reality:
`SecurityConfig` ends with `anyRequest().permitAll()` — adding
spring-boot-starter-actuator exposes endpoints **publicly**. The security
matcher must be added *with* the dependency, not deferred.

## B. Load-bearing unknowns with proposed resolutions (apply as rev 3)

| # | Unknown | Proposed resolution (engineering pin) |
|---|---|---|
| B1 | **Lap-suffix re-application on transition targets** — `call#2` completes; its transitions name bare `wait_check`; `activateNextNodes` finds no such row → **every multi-node body stalls after step 1**. Unowned by any phase | Phase 2 owns it: when the completing row has `parent_loop_step_id != null`, suffix transition targets with the row's own lap before lookup. Same layer as the strip logic |
| B2 | **Min-wins `due_at` hole** — claim doesn't clear `due_at`; poke guard (`status='PENDING'`) drops pokes while foreman is PROCESSING → lost-wake window reopened | Clear `due_at` on claim (or set sentinel); foreman re-checks "did any lap row finish while I was awake?" immediately before re-sleeping (one COUNT inside its tx). Poke stays PENDING-guarded |
| B3 | **forEach `item`/`index` never reaches body steps' scope** — defined only for the between-laps condition; body templates like `{{ item }}` have no mechanism | At stamp time, write `item`/`index` into each lap row's `config_snapshot` under reserved keys (or a small `lap_context` column); `buildRunContext` injects them for rows with `parent_loop_step_id` |
| B4 | **Phase-1 chicken-and-egg** — validator rejects unknown step types; `LoopWorkflowStep` ships Phase 3 | Phase 1 registers a **disabled stub** `LoopWorkflowStep` (id `loop`, CONTROL, full configSchema + declaredResultCodes; `execute()` returns failure "loop execution not enabled"). Workflows validate; if planned+claimed prematurely the stub refuses politely |
| B5 | **`onSupersede` location self-contradiction** (plan: "loop nodes accept… workflow-level config"; loop.md sketch: inside loop config) | **Workflow-level** (it gates run-level supersede at `plan()` time, before any node runs). One value per workflow; remove from loop-node config sketch |
| B6 | **KEEP semantics for the NEW run undefined** — as written KEEP yields two concurrent runs per person | KEEP = while a run with a live foreman exists, a new matching event is **not planned** (recorded as BLOCKED_BY_ACTIVE_LOOP). Preserves one-run-per-person |
| B7 | `onLapFailure` default unstated | Default **EXIT** (safe) |
| B8 | Fallback-wake property unnamed/unvalued | `workflow.loop.fallback-wake-minutes`, default **30** |
| B9 | Ceilings: literals vs config | Properties: `workflow.loop.max-iterations-ceiling=100`, `workflow.loop.max-rows-per-run=1000`. Note: re-validation at plan() means a tightened ceiling can start rejecting previously-saved workflows at trigger time — intended, document it |
| B10 | Foreman DB capabilities — `WorkflowStepType.execute()` has no repository access; RD-009 claims "depends only on the step contract" | Small kernel `LoopLapService` injected into `LoopWorkflowStep` (constructor DI like `ai_call`'s client). Soften RD-009 §6 wording: the *body-compatibility* invariant depends only on the contract; the foreman itself is engine-private |
| B11 | Foreman config goes through eager template resolution — body templates would resolve at foreman-wake in the wrong scope and be persisted | Foreman opts out: `resolveConfigTemplates` skips the `body` subtree for `loop` nodes (or the step type exposes a `resolvesOwnTemplates()` flag) |
| B12 | `applyTransition` dead-end/poke branch phase ownership + `transitions:{}` currently = run-failing error | Phase 2 owns the branch, **guarded on `parent_loop_step_id != null`** (zero behavior change for non-loop rows, keeping Phase 2's "existing workflows unaffected" signal honest) |
| B13 | Capped forEach list (`items.length > maxIterations`) exit unstated | Process first N laps then exit **EXHAUSTED**; non-array/null `items` → **LOOP_FAILED** (foreman-internal error); JSONata scalar auto-singleton = 1-item list |
| B14 | S18 (sibling main-flow branch hits terminal mid-lap) outcome undefined | Define + test: terminal sweep SKIPs PENDING/WAITING (incl. foreman); in-flight PROCESSING lap rows complete and their side effects fire (documented, accepted); their post-completion transitions no-op against a finalized run; poke against SKIPPED foreman is dropped by the status guard |
| B15 | Crash-state harness states under-defined | Construct via SQL on Testcontainers: (1) lap row PROCESSING with backdated `updated_at` → stale recovery path; (2) foreman PENDING + due, lap rows for notebook.lap all terminal but next lap absent (simulates crash between count and stamp under any future tx split) → foreman wake re-stamps idempotently; (3) poke landed (`due_at=now`) while notebook says lap in flight → rollover. Each with expected end state |
| B16 | `lastLap` content for LAP_FAILED rows (resultCode is NULL on failed rows) | `lastLap.<node>` = `{ resultCode, outputs, failed: bool, errorMessage }`; failed rows present with `failed=true`, `resultCode=null`. Conditions should test `failed` |
| B17 | Kill-switch ordering + routing | Drain-then-exit: disabled foreman stops stamping; on wake after current lap drains, exits `LOOP_FAILED` with `outputs.exitReason="DISABLED"`. Author transitions still route it (mandatory-wired) |
| B18 | `loop.supersede.restarts` "per person" = metric-tag cardinality bomb | Plain counter + structured log line with personId; per-person analysis via logs/DB, never a metric tag |
| B19 | Phase 6 premise "lap fields already on step DTO" is false | Assign DTO field additions (`parentLoopStepId`, `lapNumber` on `WorkflowRunStepDetail`) to Phase 2 (two fields, trivially small) |
| B20 | MVP acceptance JSON — three docs describe three bodies | Pin to [MVP-WF.md](../../../product-discovery/WORKFLOWS/MVP%20WF/MVP-WF.md): body = `wait_and_check_communication` **only** (human agent calls); full flow incl. post-task re-check leg. Fix loop.md sketch (currently shows `ai_call`) and README acceptance line ("call → wait → check") |

## C. Smaller in-phase items (a builder decides + documents; listed so nothing is rediscovered)

- `mode` required; `condition` required for while/until and forbidden for forEach; `items` vice-versa; `itemVar`/`indexVar` only in forEach; reject reserved scope names (`person,now,event,steps,lap,lastLap,item,index,sourcePersonId`) as itemVar/indexVar.
- Author node-id length cap inside bodies (suffix must fit `node_id` length 128).
- `steps.` rejection in conditions: regex like the existing person-field check; document string-literal false-positive edge.
- Suffix strip rule: single `#<int>` at end; anything else (e.g. `call#x`) = resolution error, not pass-through.
- Migration: FK `parent_loop_step_id` ON DELETE CASCADE (self-FK; matches run-cascade deletes for future retention); named constraints per convention; mirror index in entity `@Table`.
- Poke also bumps `updated_at` (repo convention).
- Min-wins / stale-counter-reset apply **only to foreman rows** (branch on step type or relation column) — never to `ai_call`-style reschedulers (global change would alter existing semantics; LEAST() against past due_at would hot-loop them).
- `materializeSteps` "skips body nodes" is already true structurally (body lives in config, not `graph.nodes`) — assert with a test, don't hunt for a code change.
- Stamping helper recipe: body-entry row PENDING `due_at=now+lapDelay(laps≥2)`; non-entry rows WAITING_DEPENDENCY with body-local predecessor counts; `depends_on_node_ids` stores suffixed ids; `config_snapshot` copied per lap row.
- `while` + `lastLap.*` condition is always falsy before lap 1 → instant ZERO_LAPS; add validator warning.
- Conformance fixture factory: per-type fixture registry (FakeFollowUpBossClient precedent in test/race/), not a generic generator; "executes in a body" assertion = completes with any declared code + lap advances.
- Scenario-suite drive mode: manual claim-pump with test Clock (existing pattern), except S3 which runs poller-on.
- `LAP_FAILED` touchpoint inventory (rebuild was needed once — keep): `checkRunCompletion` :403-414, `applyTerminalTransition` :342, `skipClaimedStepForNonPendingRun` :459, `markClaimedStepFailedAfterWorkerException` :163, `RECOVER_STALE_SQL` CASE (claim repo :60-76 — **the branch lives partly in SQL**), `WorkflowRunControlService` :79, `WorkflowRunQueryService.toStepCompletedAt` :172, UI status badge map.

## D. Cross-doc nits to fix in the same rev 3 pass

1. RD-009 §2 still shows nested suffix `call#2#5` while rev 2 defers nesting (stale example).
2. RD-009 §2 first clause still calls UNIQUE the "idempotency guard" — later text + plan correctly say "backstop only, pre-check primary".
3. plan rule 10 "engine config" vs loop.md "engine ceiling" for maxIterations — unify per B9.
4. phases.md Phase 3 row omits `onLapFailure` (tracker drift).
5. Kill-switch wording differs between plan Phase 3 and loop.md — pin per B17.

## E. Key engine facts that drove everything (verified at the time; re-verify on resume)

- Postgres aborts the tx on unique violation — "swallow and continue" needs a pre-check or savepoint.
- `WorkflowRunStepEntity` has **no `@Version`** — any JPA save is a full-column last-writer-wins overwrite; this is why the poke must be a targeted JDBC UPDATE.
- `stale_recovery_count` never resets and the requeue limit defaults to **1** — lethal to months-lived rows without the reset policy.
- One scheduler thread executes all steps serially; FUB client had no timeouts (separate fix may have landed since — check).
- An active run's status is PENDING its whole life (no RUNNING) — supersede sees long-lived loops as candidates forever.
- `RunSupersedePolicy`: for the MVP trigger (`assignedUserId.changed`), trigger-match ⇒ field-overlap, so every reassignment supersedes.

## F. What was built instead (Path A) — and the migration path back

Path A: `wait_and_check_communication` gains `maxAttempts` (default 1 = existing
behavior) + `retryDelayMinutes`, looping internally via
`StepExecutionResult.reschedule()` + `step_state` attempt counter (the `ai_call`
pattern). Covers every single-step retry loop including the MVP.

When the primitive is built, Path-A workflows migrate naturally: a single-step
body loop is the degenerate case of the foreman model — the step's own
`maxAttempts` simply stays at 1 inside a loop body. No conflict, no rework.
