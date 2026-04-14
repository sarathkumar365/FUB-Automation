# Workflow Engine Rebuild — Documentation Index

The workflow engine is being rebuilt in tracked **waves**. Each wave has its own folder under `waves/` containing all phased implementation notes for that wave.

## Top-level docs

| Doc | Purpose |
|---|---|
| [phases.md](phases.md) | Wave status tracker + links into every wave folder. Start here. |
| [research.md](research.md) | Feature research entrypoint (required by the repo feature workflow). |
| [rebuild-plan-findings.md](rebuild-plan-findings.md) | Consolidated senior-review findings that motivated the rebuild. |

## Waves

### [Wave 1 — Foundations](waves/wave-1/) — `COMPLETED`
- [phase-1-implementation.md](waves/wave-1/phase-1-implementation.md)

### [Wave 2 — Stabilization](waves/wave-2/) — `IN_PROGRESS`
- [phase-2-implementation.md](waves/wave-2/phase-2-implementation.md)

### [Wave 3 — Dynamic Trigger Routing + Retry Primitive](waves/wave-3/) — `COMPLETED`
- [wave-3-plan.md](waves/wave-3/wave-3-plan.md) — full wave plan, split into sub-phases 1–4
- [phase-3-implementation.md](waves/wave-3/phase-3-implementation.md) — implementation notes per sub-phase
- [closing-plan.md](waves/wave-3/closing-plan.md) — wave-closing rolling plan

### [Wave 4 — Admin API + Builder UI + Operator Controls](waves/wave-4/) — `PLANNED`
- [phase-4a-implementation-plan.md](waves/wave-4/phase-4a-implementation-plan.md) — admin REST API (4a)
- Wave 4b (builder UI) and 4c (operator controls) not yet drafted.

## Conventions

- Each wave is a self-contained folder under `waves/wave-{N}/`.
- A wave may contain multiple sub-phase docs (e.g. `phase-4a-*.md`, `phase-4b-*.md`).
- Cross-wave docs (`phases.md`, `research.md`, `rebuild-plan-findings.md`) stay at the top level.
- Within a wave, link siblings via relative paths (e.g. `./phase-3-implementation.md`); link to top-level docs via `../../`.
