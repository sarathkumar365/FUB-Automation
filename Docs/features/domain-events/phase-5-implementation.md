# Phase 5 — Implementation log (run-collision handling)

> **Status:** Done (2026-06-03). Closes the domain-events feature. Built as **field-aware supersede-at-plan-time**, a re-rescope from the cancel-only spec — see the `phases.md` changelog and known-issue #29.

## What Phase 5 delivered

When a newer triggering event arrives for a `(workflow, person)` that already has an in-flight run, the stale run is **superseded** (cancelled, `reason_code = SUPERSEDED_BY_NEWER_EVENT`, attributed to the newer `domain_event_id`) and the newer run — which the event already spawned under Rail 2 — proceeds to enforce the newest state. One run, on the freshest data, no stale/duplicate action.

## The decisions that shaped it

- **Cancel-only → supersede.** The spec called for cancel-only ("cancel the stale run, start nothing") to avoid building supersede's restart machinery. But under Rail 2 the replacement run **already exists** — the newer event passed the filter and spawned its own run. So "supersede" (cancel stale, newer enforces) costs the same as cancel-only while *enforcing* the newest change instead of dropping it. Cancel-only's accepted limitation ("newer change unenforced") simply evaporates.
- **No freshness gate.** The original "ideal" paired supersede with an action-step freshness re-check, for the edge where a cancelled run had already acted. That edge doesn't arise here: the stale run's irreversible actions sit behind 3-/30-min waits, and supersede cancels it the moment the newer event is processed — always before it acts. Investigated and confirmed; the freshness gate was designed and then dropped as unnecessary.
- **Field-aware, via `changed_fields` overlap.** A newer event supersedes an in-flight run only when their `changed_fields` intersect. This was the answer to a real question: "a phone change shouldn't cancel an assignment-premised run." Overlap on the change-set is the precise gate — and it's *better* than an entity-type check, because only `person.state_changed` carries `changed_fields` (so calls/notes/`person.created` are excluded for free, and `person.created` — a person event with no transition — is correctly excluded where an `entityType == person` check would wrongly include it).
- **Extracted `RunSupersedePolicy`** rather than inlining the decision in `plan()`. Keeps `plan()` thin and documents the entity-generalization seam: supersede is person-scoped because person is the only stateful entity today (`workflow_runs.source_person_id` is the run's only identity). If a second entity ever emits `*.state_changed`, generalize the identity key in one place; the field-overlap logic carries over unchanged. We explicitly chose *not* to generalize the run model now (YAGNI — no consumer, and it would be schema surgery).
- **Reused the cancel internals.** `WorkflowRunControlService.supersede` shares `finalizeCanceled` with the operator `cancelRun` (step-skip + status set). The due-worker's `runs.status = PENDING` claim filter and the executor's run-status re-check already stop a cancelled run cleanly — no new stop machinery. The spec's mooted `BLOCKED`-guard relaxation was dropped: `BLOCKED` is never set; active = `PENDING`, which the cancel path already permits.

## Surprises

- **`BLOCKED` is dead.** The spec assumed waiting runs are `BLOCKED` and that the cancel guard needed relaxing to reach them. In reality a run stays `PENDING` throughout; dependency-waiting lives at the *step* level (`WAITING_DEPENDENCY`). So "active run" = `PENDING`, and the existing guard already permits cancelling it.
- **Ordering matters.** Supersede runs *after* the idempotency check, so a webhook replay (same idempotency key → `DUPLICATE_IGNORED`) never supersedes; it only fires for genuinely new events.

## Accepted residual

Truly-simultaneous same-person events can both miss each other's not-yet-committed run (no lock / no partial unique index) → two runs proceed. Doubly-rare after Phases 2–4; tracked in known-issue #29 with a revisit-trigger (partial unique index or a per-step freshness backstop if the data ever justifies it).

## Validation

- `RunSupersedePolicyTest` (unit) — overlap supersedes; non-overlap and no-`changed_fields`/no-person no-op; only overlapping runs among many.
- `WorkflowRunControlServiceTest` (unit) — supersede cancels `PENDING` with reason + `domain_event_id` + step-skip; no-ops on non-`PENDING`/missing.
- `WorkflowTriggerRouterIntegrationTest` (Testcontainers) — overlapping events supersede; non-overlapping both survive; per-`workflow_key` scoping.
- Full suite green (715 run, 2 skipped).

## Repo decisions impact

`No` — collision handling is feature-internal. It flips the documented choice in known-issue #29 (cancel-only/deferred-supersede → adopted field-aware supersede; the residual is now the simultaneity race), updated in place. No new or changed `Docs/repo-decisions/` entry.
