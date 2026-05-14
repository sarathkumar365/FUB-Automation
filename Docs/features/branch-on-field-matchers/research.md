# Research

## Feature
`branch-on-field-matchers`

## Goal
Make `branch_on_field` usable without JSONata for the common cases —
specifically, give workflow authors a declarative way to skip
downstream actions (notes, mentions, tag-additions, emails…) when a
FUB person represents a **Realtor / partner agent / lender** rather
than a real client. The triggering ask was specifically about
`fub_create_note` not mentioning Realtors, but the rule generalises:
once a contact is flagged, you usually want to skip every action in
that workflow against them.

## What we already have

### The right primitive already exists

[`BranchOnFieldWorkflowStep`](../../../src/main/java/com/fuba/automation_engine/service/workflow/steps/BranchOnFieldWorkflowStep.java)
takes a JSONata `expression`, evaluates it against `RunContext`, and
maps the result to a result code via `resultMapping` /
`defaultResultCode`. Today you could already write:

```jsonc
{
  "type": "branch_on_field",
  "config": {
    "expression": "$count(lead.tags[$lowercase($) in [\"realtor\", \"agent\"]]) > 0",
    "resultMapping": { "true": "MATCHED", "false": "NOT_MATCHED" }
  }
}
```

…and the engine would route the workflow correctly. The problem isn't
capability — it's ergonomics. Authors need to know JSONata, handle
case-insensitivity by hand, and combine clauses with `and`/`or`
themselves. The intent (*"skip Realtors"*) is buried in expression
syntax.

### Lead data at step execution

[`RunContext.lead`](../../../src/main/java/com/fuba/automation_engine/service/workflow/RunContext.java)
is a Map view of `leads.lead_details` (JSONB), refreshed per step by
`LeadSnapshotResolver`. Snapshot fields are pinned in
[`LeadUpsertService.SNAPSHOT_FIELDS`](../../../src/main/java/com/fuba/automation_engine/service/lead/LeadUpsertService.java)
and include the discriminators we need:

- `tags` (list of strings, FUB-synced) ← primary signal for Realtor
- `type` (`"Buyer"`, `"Seller"`, …)
- `stage`, `stageId`, `source`
- `assignedUserId`, `assignedTo`, `assignedPondId`, `assignedLenderId`
- `claimed`, `contacted`

Snapshots are refreshed on every `peopleCreated` / `peopleUpdated`
webhook in `WebhookEventProcessorService`, so the data is current at
the moment a workflow step executes — no live FUB call needed.

### Expression evaluator path resolution

[`ExpressionEvaluator.evaluatePredicate(expression, scope)`](../../../src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionEvaluator.java)
already evaluates JSONata against
[`ExpressionScope`](../../../src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java),
which exposes `lead.*`, `event.payload.*`, `steps.<id>.outputs.*`,
`now.*`, and `sourceLeadId`. We can reuse this for the declarative
mode: treat `field` as a JSONata path expression and evaluate it the
same way. That gives us full path coverage for free, including
arrays, scoped step outputs, etc.

### FUB API confirmation

FUB distinguishes two entity types:

- **Users** (`/users`) — internal team members with a `role`
  (`"Agent"`, `"Broker"`, `"Lender"`). These are the people we
  @mention.
- **People** (`/people`) — external contacts. Notes attach to these.
  **People have no `role` field.**

A Realtor stored in our system is therefore a **Person tagged as**
`"Realtor"` / `"Agent"` / `"Partner Agent"` / `"Lender"` (the
convention is team-specific in FUB; the codebase carries no hardcoded
tag dictionary). Tag-based discrimination is the only reliable path.
`lead.type` / `lead.stage` are secondary signals.

## Design alternatives considered

1. **Add `skip` config block to `fub_create_note`.** Step-local —
   every other action would need the same config duplicated, and the
   "skip realtors" rule would drift across workflows. **Rejected.**
2. **Generic `skipIf` on `WorkflowStepType` interface.** Most
   powerful, but a large engine change with new universal `SKIPPED`
   semantics. `branch_on_field` already covers the power-user case.
   **Rejected** for now.
3. **New `branch_on_lead_tags` step.** Single-purpose, ergonomic for
   the tag case, but **overlaps with `branch_on_field`** — two
   conditional-flow primitives in the engine, two mental models,
   harder to audit later. **Rejected.**
4. **Extend `branch_on_field` with a declarative matcher mode that
   sits alongside `expression`.** Reuses one step; expression mode
   stays for power users; tag/type/stage filtering becomes
   fill-in-the-blanks for everyone else. **Chosen.**

## Decision

Extend `BranchOnFieldWorkflowStep` with an **alternative config
shape** (mutually exclusive with `expression`) that supports:

- **Leaf condition**: `{ field, op, values?, match? }`.
- **Composite conditions**: `{ allOf: [...] }` and `{ anyOf: [...] }`,
  which compose leaves and other composites recursively (so
  `"Realtor AND (Buyer OR Seller)"` is expressible without JSONata).

Matcher mode produces a boolean; routes through the existing
`resultMapping` / `defaultResultCode` pipeline. **No changes** to
`FubCreateNoteWorkflowStep`, the FUB client, the engine, the schema,
or any other step. **No regression** in the expression-mode path —
existing workflows are bit-identical.

Operators in v1:

- `containsAny` — field-as-list, true if any element matches any
  `values` entry under the chosen `match` rule.
- `equalsAny` — field-as-scalar equality against any `values` entry.
- `exists` — field resolves to non-null (and non-blank for strings).
- `missing` — inverse of `exists`.

Match rules (string-compare only; ignored for numbers/booleans):
`ci-exact` (default) | `ci-contains` | `cs-exact`.

## Non-goals

- A central "Realtor tag dictionary." Keep per-workflow until real
  duplication appears.
- More operators (`gt`/`lt`/`between`/`regex`). Add when needed.
- Modifying `fub_create_note` itself. Stays single-purpose.
- A universal `skipIf` on the base `WorkflowStepType` interface.
