# Step Type Reference

Canonical per-step reference docs for the workflow engine.

## Convention

- **One file per step type**, named `<step-id>.md` (the step's `WorkflowStepType.id()` value).
- **Purpose**: read top-to-bottom by a workflow author who wants to know what
  the step does, how to configure it, what it returns, and how to wire it up.
  Not a design doc — those live in `Docs/features/<feature-name>/`.
- **Tone**: reference, not narrative. Lead with a one-line purpose, then
  config schema, then result codes, then examples, then known limits.
- **Cross-link** the design/research doc that introduced the step, so authors
  can dig deeper when needed.

## What belongs in a step doc

1. **Purpose** — one sentence.
2. **At-a-glance table** — id, retry policy, mutates state? (read-only vs
   side-effecting), expected lead context.
3. **Config schema** — every field, type, required/optional, default.
4. **Result codes** — every code the step can emit, and the typical
   downstream routing for each.
5. **Outputs** — keys put into `steps.<nodeId>.outputs.*` for later steps.
6. **Examples** — simple → complex. Real JSON, runnable.
7. **Limitations / known issues** — anything an author would otherwise
   stub their toe on. Link to known-issues file when relevant.
8. **Related docs** — design plan, research, related step types.

## What does NOT belong here

- Design rationale ("why we built it this way") — that's
  `Docs/features/<feature-name>/research.md`.
- Phased implementation notes — that's `Docs/features/<feature-name>/plan.md`
  or the relevant wave folder.
- Engine-wide concepts (run lifecycle, claim loop, expression scope) — that's
  [`how-the-engine-works.md`](../how-the-engine-works.md).

## Index

| Step ID | Doc | Status |
|---|---|---|
| `branch_on_field` | [branch-on-field.md](./branch-on-field.md) | ✅ Reference |

(Add other step types here as their reference docs land. Steps without an
entry here currently have no dedicated reference — see
[`how-the-engine-works.md`](../how-the-engine-works.md) for engine-level
context and the source under `service/workflow/steps/` for behaviour.)
