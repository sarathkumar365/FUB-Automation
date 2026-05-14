# Plan — Declarative matchers in `branch_on_field`

## Context

Workflows currently have no way to opt out of running actions
(`fub_create_note`, `fub_add_tag`, …) against records that aren't real
clients — partner Realtors, co-op agents, lenders. The triggering ask
was specifically about `fub_create_note` not mentioning Realtors, but
the rule generalises: if a contact is a Realtor, you usually also
don't want to tag them, email them, or task them.

The engine already has the right primitive: `branch_on_field`.
Today it requires a JSONata `expression` — fully capable but unfriendly
for the 90% case. This feature adds a **declarative matcher** as an
alternative to `expression`, so common checks ("tag is Realtor",
"type is Buyer", "stage exists") become fill-in-the-blanks.
JSONata stays for the power-user path.

See [research.md](./research.md) for what we have, the FUB API
context, and the alternatives we rejected.

## Approach summary

- **Modify one step**: `BranchOnFieldWorkflowStep`.
- Add an *alternative* config shape (mutually exclusive with
  `expression`):
  - **Leaf**: `{ field, op, values?, match? }`
  - **Composite**: `{ allOf: [...] }` or `{ anyOf: [...] }`, which
    compose leaves and other composites recursively.
- Matcher mode produces a boolean → routes through the existing
  `resultMapping`/`defaultResultCode` pipeline.
- **No changes** to `FubCreateNoteWorkflowStep`, the FUB client, the
  engine, the DB schema, or any other step.
- **Backward compatible**: expression-mode workflows are bit-identical.

## Config shapes

### Existing (unchanged)

```json
{
  "type": "branch_on_field",
  "config": {
    "expression": "...",
    "resultMapping": { "true": "MATCHED", "false": "NOT_MATCHED" },
    "defaultResultCode": "NOT_MATCHED"
  }
}
```

### New — leaf

```json
{
  "type": "branch_on_field",
  "config": {
    "field": "lead.tags",
    "op": "containsAny",
    "values": ["Realtor", "Agent", "Partner Agent", "Lender"],
    "match": "ci-exact",
    "resultMapping": { "true": "SKIP", "false": "PROCEED" },
    "defaultResultCode": "PROCEED"
  }
}
```

### New — composite (`allOf` / `anyOf`, nestable)

```json
{
  "type": "branch_on_field",
  "config": {
    "allOf": [
      { "field": "lead.tags", "op": "containsAny", "values": ["Realtor"] },
      { "anyOf": [
          { "field": "lead.type",  "op": "equalsAny", "values": ["Buyer"] },
          { "field": "lead.stage", "op": "equalsAny", "values": ["Hot"]   }
        ]
      }
    ],
    "resultMapping": { "true": "SKIP", "false": "PROCEED" }
  }
}
```

Reads as: *"Realtor AND (Buyer OR Hot stage)"*.

### Condition grammar

A **condition** is **one** of:

- a **leaf**: `{ field: string, op: enum, values?: array, match?: enum }`
- `{ allOf: [<condition>, <condition>, ...] }` — true iff all true
- `{ anyOf: [<condition>, <condition>, ...] }` — true iff any true

Short-circuit evaluation in both directions.

### Operators (v1)

| `op`          | Field shape    | True when…                                      |
| ------------- | -------------- | ----------------------------------------------- |
| `containsAny` | list (or scalar coerced to 1-elem list) | any element matches any `values` entry under `match` |
| `equalsAny`   | scalar         | scalar equals any `values` entry under `match` (lists → `CONFIG_INVALID`) |
| `exists`      | any            | field resolves to non-null (and non-blank string) |
| `missing`     | any            | inverse of `exists`                              |

### Match rules (string compare only — ignored for non-strings)

- `ci-exact` (default) — `equalsIgnoreCase`
- `ci-contains` — case-insensitive substring
- `cs-exact` — case-sensitive `equals`

## Mode selection (refined for back-compat)

Matcher mode is **triggered by any of** `op`, `allOf`, or `anyOf` at
the top level. Trigger rules:

- only `expression` present → expression mode (existing path)
- only one of `op` / `allOf` / `anyOf` present → matcher mode
- `expression` together with any matcher key → `CONFIG_INVALID`
- nothing present → `EXPRESSION_MISSING` (preserves current behaviour)

At each nesting level: exactly one of `{ field/op/values/match }` *or*
`allOf` *or* `anyOf`. Mixing → `CONFIG_INVALID`.

## Result codes

Existing codes preserved + one new:

- `EXPRESSION_MISSING` — neither `expression` nor matcher keys present.
- `EXPRESSION_EVAL_ERROR` — field-path evaluation throws.
- `NO_MATCHING_RESULT` — no `resultMapping` hit and no `defaultResultCode`.
- **New** `CONFIG_INVALID` — config shape error (mixing modes, unknown
  `op`/`match`, empty `allOf`/`anyOf`, `equalsAny` got a list, `values`
  missing for required-values ops, `values` supplied for `exists`/`missing`).

`declaredResultCodes()` stays `Set.of()` (dynamic codes).

## Outputs

- Expression mode (unchanged): `{ expressionResult: "<stringified>" }`.
- Matcher mode: `{ expressionResult: "true"|"false" }` — same key as
  expression mode so any downstream `steps.<id>.outputs.expressionResult`
  read keeps working — **plus** `matchResult: true/false` and (for
  leaves at top-level) `matchedValue: <which configured value matched>`
  for richer audit.

## Example: replace the original Realtor-skip workflow

```jsonc
{
  "entryNode": "guard",
  "nodes": [
    {
      "id": "guard",
      "type": "branch_on_field",
      "config": {
        "field": "lead.tags",
        "op": "containsAny",
        "values": ["Realtor", "Agent", "Partner Agent"],
        "match": "ci-exact",
        "resultMapping": { "true": "SKIP", "false": "PROCEED" }
      },
      "transitions": {
        "SKIP":    { "terminal": "COMPLETED" },
        "PROCEED": { "next": "note" }
      }
    },
    {
      "id": "note",
      "type": "fub_create_note",
      "config": {
        "message": "Auto-followup …",
        "mentionUserIds": [12],
        "mentionUserNames": ["Jane"]
      },
      "transitions": {
        "SUCCESS": { "terminal": "COMPLETED" },
        "FAILED":  { "terminal": "FAILED" }
      }
    }
  ]
}
```

Same shape works for `lead.type`, `lead.stage`, `lead.source` — just
swap `field` and `op`. For AND/OR combinations, wrap in `allOf`/`anyOf`.

## Work items

### 1. Extend `BranchOnFieldWorkflowStep`

File: `src/main/java/com/fuba/automation_engine/service/workflow/steps/BranchOnFieldWorkflowStep.java`

- Add `CONFIG_INVALID` constant.
- Extend `configSchema()` to declare `field`, `op`, `values`, `match`,
  `allOf`, `anyOf` alongside the existing expression keys. Document
  mutual-exclusion in field descriptions (runtime enforces it).
- In `execute(...)`:
  1. Read top-level config; classify as `expression` mode, matcher
     mode, or unset.
  2. If `expression` mode → unchanged code path.
  3. If matcher mode → recursively evaluate the condition tree:
     - leaf: resolve `field` via
       `expressionEvaluator.evaluatePredicate(field, scope)` (wrap
       errors into `EXPRESSION_EVAL_ERROR`), apply operator, return bool.
     - `allOf`: short-circuit AND across children.
     - `anyOf`: short-circuit OR across children.
  4. Stringify the boolean (`"true"`/`"false"`) and route through the
     existing `resultMapping`/`defaultResultCode` block.
  5. Outputs: include `expressionResult` (backward compat) +
     `matchResult` + (leaf-only) `matchedValue`.
- Inner static class `Condition` (sealed: `Leaf` / `AllOf` / `AnyOf`)
  to keep `execute` readable. Parser builds the tree from the Map
  config in one pass; surface bad shapes as `CONFIG_INVALID`.

### 2. Tests

File: `src/test/java/com/fuba/automation_engine/service/workflow/BranchOnFieldWorkflowStepTest.java`
(no such file exists today; place alongside `FubCreateNoteWorkflowStepTest`
which is at the same level, matching that convention).

Cases (26 total — see canonical plan for the full matrix):

- Expression-mode smoke test (back-compat).
- Leaf: `containsAny` hits/misses for `ci-exact` and `ci-contains`.
- Leaf: `containsAny` with null/missing field, with scalar field
  coerced to list.
- Leaf: `equalsAny` hits/misses, lists → `CONFIG_INVALID`.
- Leaf: `exists` / `missing` on present, missing, and blank fields.
- Config errors: both modes present, neither present, unknown
  `op`/`match`, `values` missing/forbidden per op.
- Composites: `allOf` (true × true, short-circuit false), `anyOf`
  (short-circuit true, all false), nested `allOf`/`anyOf`.
- Composite config errors: empty `allOf`/`anyOf`, mixing
  `allOf`+`anyOf` at same level, mixing composite + leaf at same level.
- Result-code routing: mapping hit, fall back to `defaultResultCode`.

## Critical files to touch / read

**Modify**
- `src/main/java/com/fuba/automation_engine/service/workflow/steps/BranchOnFieldWorkflowStep.java`

**Add**
- `src/test/java/com/fuba/automation_engine/service/workflow/BranchOnFieldWorkflowStepTest.java`

**Read for patterns (no edits)**
- `service/workflow/expression/ExpressionEvaluator.java` —
  `evaluatePredicate(...)` does the path resolution.
- `service/workflow/expression/ExpressionScope.java` — confirms
  `lead`/`event.payload`/`steps`/`now`/`sourceLeadId` namespaces.
- `service/workflow/RunContext.java` &
  `service/lead/LeadUpsertService.java` — `lead.tags`, `lead.type`,
  `lead.stage` are populated in the snapshot.
- `service/workflow/StepExecutionResult.java` — `success(...)` /
  `failure(...)` factories.
- `service/workflow/FubCreateNoteWorkflowStepTest.java` — test
  harness shape for `StepExecutionContext` / `RunContext` construction.

## Reused existing utilities

- `ExpressionEvaluator.evaluatePredicate(field, scope)` — path
  resolution for leaf reads (so `field: "lead.tags"` Just Works).
- Existing `resultMapping`/`defaultResultCode` routing — both modes
  produce a boolean and fall through the same pipeline.
- Spring `@Component` auto-discovery — no registration changes.

## Out of scope

- A generic `skipIf` on the base `WorkflowStepType` interface.
- Modifying `FubCreateNoteWorkflowStep` itself.
- Additional operators (`gt`/`lt`/`between`/`regex`).
- A shared "Realtor tag dictionary" across workflows.

## Verification

1. **Unit tests**: full matrix passes via
   `mvn test -Dtest=BranchOnFieldWorkflowStepTest`.
2. **Full build**: `mvn -q -DskipITs verify` green; no other tests
   regress.
3. **End-to-end via `/admin/workflows`**:
   - POST the example workflow above with `values: ["Realtor"]`.
   - Trigger against a lead tagged `"Realtor"` → run history: `SKIP`
     → `COMPLETED`; no note in FUB.
   - Trigger against a clean lead → run history: `PROCEED` →
     `SUCCESS`; note visible in FUB (verify with `fub-api-test` skill
     or the FUB UI).
4. **Backward compatibility**: existing workflows using only
   `expression` produce identical result codes and identical
   `expressionResult` outputs (covered by test #1).
