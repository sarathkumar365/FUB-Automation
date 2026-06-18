# RD-010: Step types carry a category — CONTROL / UTILITY / BUSINESS, CI-enforced

## Status
Accepted (2026-06-10). Implementation rides the loop-primitive feature as
**Phase 0** — see [`loop-primitive/plan.md`](../features/workflow-engine/loop-primitive/plan.md).

## Context
The step catalog is growing along two distinct lines: engine primitives (delay,
branch, the planned loop) and business/vendor actions (fub_*, ai_call). The
distinction already exists implicitly in two binding places — the engine extraction
(shipped as the standalone `camshaft-engine` repo) declared FUB steps "host glue, OUT"
of the extractable kernel, and the loop design
([RD-009](RD-009-loop-primitive-foreman-cloned-rows.md)) introduces a "control
family" — but nothing in code records or enforces it. Nothing today stops a control
step from growing a FUB import and silently poisoning the extraction boundary.

## Decision
Every `WorkflowStepType` declares a **category** (enum `StepCategory`, exposed via a
`category()` method on the interface and surfaced in the admin step-catalog endpoint):

- **CONTROL** — engine primitives that shape execution; no business meaning.
- **UTILITY** — generic, domain-neutral actions usable by any deployment
  (vendor-backed is acceptable — e.g. Slack — as long as it is domain-neutral).
- **BUSINESS** — domain/vendor-specific actions tied to this host application.

**Extraction mapping:** CONTROL + UTILITY ship with the engine library;
BUSINESS stays in the host.

**Enforcement:** an architecture test in CI asserts that CONTROL and UTILITY step
classes do not import host/FUB packages. The category is a guarded fact, not a label.

### Classification of current steps (owner-decided 2026-06-10)

| Category | Steps |
|---|---|
| CONTROL | `delay`, `branch_on_field`, `loop` (planned) |
| UTILITY | `set_variable`, `http_request`, `slack_notify` |
| BUSINESS | `fub_create_task`, `fub_create_note`, `fub_reassign`, `fub_move_to_pond`, `fub_add_tag`, `wait_and_check_communication`, `wait_and_check_claim`, `ai_call` |

Judgment calls recorded: `slack_notify` = UTILITY (domain-neutral despite vendor);
`ai_call` = BUSINESS (lead-domain today; revisit if the AI-call service becomes a
generic capability).

## Consequences
- New step types must declare a category at creation; the catalog endpoint and step
  reference docs (`steps/README.md` index) display it.
- Moving a step's category is a reviewed decision (update this RD), not a drive-by edit.
- The ArchUnit-style boundary test becomes the executable form of the engine/host
  step boundary; extraction work inherits it.
