# Workflow Engine Rebuild — Documentation Index

The workflow engine is being rebuilt in tracked **waves**. Each wave has its own folder under `waves/` containing all phased implementation notes for that wave.

## Sample Workflow - consists 2 section. A Trigger & Steps

┌─────────────────────────────────────────┐
│  Workflow: "New Lead Follow-up"         │
│                                         │
│  ┌─ TRIGGER ─────────────────────────┐  │
│  │  Type: FUB Webhook                │  │
│  │  Domain: PEOPLE                   │  │
│  │  Action: CREATED                  │  │
│  │  Filter: (optional)               │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌─ STEPS ───────────────────────────┐  │
│  │  1. add_tag ("Hot Lead")          │  │
│  │  2. wait (5 minutes)              │  │
│  │  3. send_email (welcome template) │  │
│  └───────────────────────────────────┘  │
│                                         │
│  [Save Draft]  [Activate]               │
└─────────────────────────────────────────┘

## Top-level docs

| Doc | Purpose |
|---|---|
| [phases.md](phases.md) | Wave status tracker + links into every wave folder. Start here. |
| [research.md](research.md) | Feature research entrypoint (required by the repo feature workflow). |
| [rebuild-plan-findings.md](rebuild-plan-findings.md) | Consolidated senior-review findings that motivated the rebuild. |
| [workflow-engine-implementation-plan.md](workflow-engine-implementation-plan.md) | Original implementation-plan narrative and section breakdown. |
| [workflow-engine-technical-implementation.md](workflow-engine-technical-implementation.md) | Detailed technical design and schema-level notes. |
| [policy-engine-rebuild-analysis-and-plan.md](policy-engine-rebuild-analysis-and-plan.md) | Legacy analysis document that informed the workflow rebuild direction. |
| [scenarios/lead-intake-call-attempt-workflow/](scenarios/lead-intake-call-attempt-workflow/) | Scenario pack for complex lead-intake/call-attempt workflow and capability-gap planning. |

## Waves

### [Wave 1 — Foundations](waves/wave-1/) — `COMPLETED`
- [phase-1-implementation.md](waves/wave-1/phase-1-implementation.md)

### [Wave 2 — Stabilization](waves/wave-2/) — `COMPLETED`
- [phase-2-implementation.md](waves/wave-2/phase-2-implementation.md)

### [Wave 3 — Dynamic Trigger Routing + Retry Primitive](waves/wave-3/) — `COMPLETED`
- [wave-3-plan.md](waves/wave-3/wave-3-plan.md) — full wave plan, split into sub-phases 1–4
- [phase-3-implementation.md](waves/wave-3/phase-3-implementation.md) — implementation notes per sub-phase
- [closing-plan.md](waves/wave-3/closing-plan.md) — wave-closing rolling plan

### [Wave 4 — Admin API + Builder UI + Operator Controls](waves/wave-4/) — `COMPLETED` (4a complete; 4c cancel controls complete; 4b/retry deferred)
- [phase-4a-implementation-plan.md](waves/wave-4/phase-4a-implementation-plan.md) — admin REST API (4a)
- [phase-4a-implementation.md](waves/wave-4/phase-4a-implementation.md) — implementation notes (Wave 4a completed)
- [phase-4c-implementation-plan.md](waves/wave-4/phase-4c-implementation-plan.md) — operator cancel controls plan (4c)
- [phase-4c-implementation.md](waves/wave-4/phase-4c-implementation.md) — implementation notes (Wave 4c cancel-only)
- Wave 4b (builder UI) plus run/step retry controls remain deferred.

### [Wave 5 — Migration Cutover](waves/wave-5/) — `COMPLETED` (Pass 1-5 completed; workflow-only cutover validated and legacy policy surface removed)
- [migration-cutover-plan.md](waves/wave-5/migration-cutover-plan.md) — migration contract and pass plan
- [phase-1-implementation.md](waves/wave-5/phase-1-implementation.md) — Pass 1 implementation notes
- [phase-2-implementation.md](waves/wave-5/phase-2-implementation.md) — Pass 2 implementation notes
- [phase-3-implementation.md](waves/wave-5/phase-3-implementation.md) — Pass 3 implementation notes
- [phase-5-implementation.md](waves/wave-5/phase-5-implementation.md) — Pass 5 implementation notes

## Conventions

- Each wave is a self-contained folder under `waves/wave-{N}/`.
- A wave may contain multiple sub-phase docs (e.g. `phase-4a-*.md`, `phase-4b-*.md`).
- Cross-wave docs (`phases.md`, `research.md`, `rebuild-plan-findings.md`) stay at the top level.
- Within a wave, link siblings via relative paths (e.g. `./phase-3-implementation.md`); link to top-level docs via `../../`.
