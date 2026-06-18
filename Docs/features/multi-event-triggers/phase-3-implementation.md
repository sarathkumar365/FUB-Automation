# Phase 3 — Router + integration

> 2026-06-18 · Status: implemented, full suite green (753); committed.

## Goal

Make the router actually route `anyOf` triggers. Until now the pre-filter skipped any
trigger with no top-level `on` — which is every `anyOf` trigger — so the matcher (Phase 1)
and validator (Phase 2) were inert end-to-end. This phase wires them up.

## What changed

`WorkflowTriggerRouter.route()` — the two skip checks (the `:81` null-`on` guard and the
`:85` kind equality) collapse into one:

```
if (!ParsedTrigger.from(trigger).subscribesTo(event.eventKind())) { skip }
```

`subscribesTo` returns true if *any* entry's kind matches, so flat and `anyOf` are handled
uniformly; a null/empty trigger yields no entries and is skipped (the prior behaviour). The
dead `TRIGGER_ON_KEY` constant is removed. The echo gate and the `matches()` try/catch are
untouched.

## Tests

- **Unit** (`WorkflowTriggerRouterTest`) — `shouldRouteAnyOfTriggerSubscribedToEventKind`:
  an `anyOf` workflow now clears the pre-filter (previously skipped). The existing
  null-trigger / wrong-kind skip test still passes.
- **R1** (`WorkflowTriggerRouterIntegrationTest`) — the Rita repro, both directions: a
  `person.created`-assigned event plans a run, a `person.state_changed` reassignment plans a
  run, a `state_changed` with no assignment does not. Ran against Postgres.
- **R3** — an engine-caused event is skipped by the echo gate before matching.

## R2 — not added (accepted as covered)

The plan listed R2 (supersede across entry paths). It is **not** a separate test:

- Supersede is keyed on `(workflow_key + person)` + changed-field overlap and is *agnostic*
  to which entry/kind triggered each run. "One `anyOf` workflow supersedes across both kinds"
  follows directly from supersede being key-scoped — already proven by
  `shouldScopeSupersedePerWorkflowKey` and `shouldSupersedeInFlightRunWhenNewerEventOverlapsChangedFields`.
- A router-based R2 also collapses under the harness's null-event-id dedup (key+source+person),
  yielding one run with nothing to supersede; forcing it would need seeded `events`-table rows.

Accepted by the owner rather than duplicating existing coverage.

## Validation

- Trigger unit layer: 50 green. Full suite: 753 tests, 0 failures, 2 skipped, BUILD SUCCESS;
  the router integration class (9 tests) ran against Postgres.

## Repo decisions impact

No — local feature concern (host trigger glue); no boundary moved, no RD.

## Files

- `service/workflow/trigger/WorkflowTriggerRouter.java`
- tests `WorkflowTriggerRouterTest.java`, `WorkflowTriggerRouterIntegrationTest.java`
