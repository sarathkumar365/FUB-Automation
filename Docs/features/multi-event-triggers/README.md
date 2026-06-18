# Multi-Event Triggers (`anyOf`)

> **Status:** APPROVED 2026-06-18 — building in 4 phases.
> Design + contract: [plan.md](plan.md). Tracker: [phases.md](phases.md).

## Why

A workflow trigger today subscribes to exactly **one** event kind. Real gap found
in production testing (2026-06-11): the MVP enforcement workflow triggers on
`person.state_changed` + `change.assignedUserId.changed` — but Facebook leads
arrive **already assigned**, so the assignment is inside `person.created` and
never appears as a change. Those leads can never trigger the workflow
(person 21007 "Rita Marchese" is the documented repro: events 1186/1187,
zero runs).

Workaround rejected by owner: a duplicated companion workflow per event kind
(graph copy-drift + supersede can't deduplicate across workflow keys).

## What

Triggers gain an additive `anyOf` shape — a list of (event kind, filter) pairs,
OR'd; the workflow fires when any pair matches:

```json
"trigger": {
  "anyOf": [
    { "on": "person.created",       "filter": "person.kind = 'LEAD' and $boolean(person.assignedUserId)" },
    { "on": "person.state_changed", "filter": "person.kind = 'LEAD' and change.assignedUserId.changed and $boolean(change.assignedUserId.new)" }
  ],
  "reactToEngineEvents": false
}
```

The existing flat `{ "on": ..., "filter": ... }` shape stays valid forever
(purely additive). Bonus correctness: one workflow firing from both kinds means
`RunSupersedePolicy` (keyed on workflow_key + person) correctly supersedes
across the two entry paths — impossible with two separate workflows.

## First consumer

`agent_followup_enforcement_mvp` — its trigger is repointed to the `anyOf` example
above in **Phase 4** of this feature (admin-API new version + Rita-repro verification).
