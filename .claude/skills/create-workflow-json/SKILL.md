---
name: create-workflow-json
description: Generate a workflow JSON definition for the automation-engine. Use when the user describes a desired workflow in plain language (e.g. "when a lead is assigned, wait 3 min, post a note if no call"). Discovers available step types and triggers from the codebase, optionally inspects existing workflows in the local DB, then produces a validated JSON ready to POST to /admin/workflows.
---

# Create Workflow JSON

The automation-engine has no UI builder for workflows. Workflows are JSON documents POSTed to `/admin/workflows` and stored across the `automation_workflows` table (top-level `trigger` JSONB column + `graph` JSONB column). This skill produces those JSON documents from a plain-language description.

## Hard rule: discover before you draft

Never invent step types, trigger types, config keys, or result codes. Always discover them from the current codebase first. The taxonomy changes as new steps are added — what worked last month may not work today.

## Discovery procedure (run every time)

### 1. Enumerate available step types

```bash
ls src/main/java/com/fuba/automation_engine/service/workflow/steps/*.java
```

For each step file, extract:

- `id()` — the string used in workflow JSON `"type"` field
- `configSchema()` — required + optional config keys, types, descriptions
- `declaredResultCodes()` — the set of result codes this step can return (these are the keys in the `transitions` map). An empty `Set.of()` signals dynamic codes (e.g. `branch_on_field`).

Quick extraction:

```bash
for f in src/main/java/com/fuba/automation_engine/service/workflow/steps/*.java; do
  echo "=== $(basename $f) ==="
  awk '/public String id\(\)/,/^    }/' "$f" | head -5
  awk '/public Map<String, Object> configSchema/,/^    }$/' "$f"
  awk '/public Set<String> declaredResultCodes/,/^    }$/' "$f"
done
```

### 2. Enumerate available trigger types

```bash
ls src/main/java/com/fuba/automation_engine/service/workflow/trigger/*.java
```

Read each `WorkflowTriggerType` impl. Extract:

- `TRIGGER_TYPE_ID` / `id()` — value for `trigger.type`
- `configSchema()` — required keys (e.g. `eventDomain`, `eventAction`, `filter`)
- The `matches()` method to understand the exact filter scope — variables available inside the JSONata filter are usually narrower than step-execution scope

### 3. Inspect ExpressionScope for available template variables

```bash
cat src/main/java/com/fuba/automation_engine/service/workflow/expression/ExpressionScope.java
```

Look at top-level keys exposed (`event`, `lead`, `now`, `steps`, `sourceLeadId`, etc.). Step configs reference these via `{{ ... }}` JSONata templates. Trigger filter scope is separate and narrower — verify per trigger.

### 4. Optional: inspect existing workflows in the local DB

```bash
PGPASSWORD=sarathkumar psql -U sarathkumar -h localhost -d automation_engine -c \
  "SELECT key, status, jsonb_pretty(trigger) AS trigger, jsonb_pretty(graph) AS graph
   FROM automation_workflows WHERE status = 'ACTIVE' ORDER BY id;"
```

Prefer `ACTIVE` rows. Archived rows may use stale enum values (e.g. domain `ASSIGNMENT` was renamed to `LEAD` in V18). DB connection details: `src/main/resources/application.properties` under `spring.datasource.*`.

### 5. Verify the request DTO shape

```bash
cat src/main/java/com/fuba/automation_engine/controller/dto/CreateWorkflowRequest.java
```

The POST body must match this record's fields exactly. As of last check: `key`, `name`, `description`, `trigger`, `graph`, `status`.

## Output JSON shape

The top-level fields go to `CreateWorkflowRequest`. The `graph` object has its own internal shape with `schemaVersion`, `entryNode`, and `nodes` — verify against existing DB rows.

```json
{
  "key": "snake_case_unique_id",
  "name": "Human-readable name",
  "description": "What this workflow does and when it fires",
  "status": "ACTIVE",
  "trigger": {
    "type": "<trigger.id() from discovery>",
    "config": {}
  },
  "graph": {
    "schemaVersion": 1,
    "entryNode": "<id of the first node>",
    "nodes": [
      {
        "id": "node_id_unique_within_workflow",
        "type": "<step.id() from discovery>",
        "config": {},
        "transitions": {
          "<RESULT_CODE>": ["<next_node_id>"],
          "<OTHER_RESULT_CODE>": { "terminal": "COMPLETED" }
        }
      }
    ]
  }
}
```

### Transition value formats

- `["next_node_id"]` — array of one or more node IDs to advance to
- `{ "terminal": "COMPLETED" }` — terminate the run with a status (`COMPLETED`, `FAILED`, etc.)

There is no bare `"END"` keyword. Always use one of the two forms above.

## Authoring rules

- Every result code must be handled. For each node, check `declaredResultCodes()` and ensure every code appears as a key in `transitions` (or document why one is intentionally omitted).
- Templates use `{{ expression }}` with JSONata syntax. Reference only variables that `ExpressionScope` exposes.
- Trigger filter scope is narrower than step scope. As of last check, `FubWebhookTriggerType` filter scope only contains `event.payload` — NOT `lead.*` (see known-issues #17). To gate on `lead.*`, add a `branch_on_field` node at the entry rather than a trigger filter.
- Operator constants are inlined as literals (no `config.*` namespace exists — Phase 4 dropped). Use `__PLACEHOLDER__` markers (e.g. `__ISA_USER_ID__`) for values the user hasn't supplied; document each placeholder in the response.
- Match enum casing exactly. `eventDomain: "LEAD"`, `eventAction: "UPDATED"` — uppercase, matches `NormalizedDomain` / `NormalizedAction`.
- For `branch_on_field`, the JSONata expression result is `String.valueOf()`'d, looked up in `resultMapping`, and the mapped value becomes the result code. Keys in `resultMapping` are strings like `"true"` / `"false"` / `"42"`.

## Workflow

1. Read the user's description of intent.
2. Run discovery steps 1–3 (and 4–5 if useful).
3. Restate back: "Steps available that match your need: X, Y, Z. Trigger: T. Any constants I need?"
4. Draft the JSON with discovered IDs, schemas, and result codes.
5. Self-validate before presenting:
   - Every node `type` matches a discovered `id()`
   - Every node `config` key is declared in that step's `configSchema()`
   - Every `transitions` key matches a `declaredResultCodes()` entry (or is a dynamic code from `resultMapping` for `branch_on_field`)
   - Every `transitions` value is either an array of valid node IDs or a `{ "terminal": "..." }` object
   - The `trigger.type` matches a discovered trigger ID
   - All `{{ }}` templates reference variables present in `ExpressionScope`
   - The top-level shape matches `CreateWorkflowRequest`
6. Present the JSON in a code block, plus a short summary of each node and any placeholders to fill in.
7. Suggest where to save it (`Docs/features/<feature-name>/workflow.json`) and how to POST it to `/admin/workflows`.

## When in doubt

- If two steps could plausibly fit, ask — don't guess.
- If a result code's handling is unclear (terminal vs. branch), surface it as a question.
- If the user's intent requires a step type that doesn't exist, say so plainly and either propose a workaround using existing steps or flag the gap as out-of-scope work.

## Anti-patterns to avoid

- Inventing a step type because it "makes sense" — the engine rejects unknown types at validation.
- Copying a result code from another codebase or a hallucination — only use codes from `declaredResultCodes()`.
- Using `lead.*` in a trigger `filter` (unsupported — known-issues #17). Use a `branch_on_field` entry node instead.
- Putting JSONata in trigger config keys other than `filter` (`eventDomain` / `eventAction` are literal-match, not expressions).
- Using bare `"END"` as a transition value — use `{ "terminal": "COMPLETED" }`.
- Forgetting that `key` must be unique per `ACTIVE` workflow (DB enforces it).
