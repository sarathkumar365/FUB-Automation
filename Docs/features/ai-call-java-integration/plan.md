# AI Call Java Integration — Plan

## Goal
Implement Phase 2 Java runtime support for ai-call polling semantics via one self-rescheduling workflow step.

## Planned passes
1. Pass 1: Engine reschedule primitive + `step_state` persistence + tests.
2. Pass 2: Call-service client adapter + DTOs + config.
3. Pass 3: `AiCallWorkflowStep` implementation + step tests.
4. Pass 4: Integration wiring + catalog visibility + regression validation.

## Progress
- Pass 1: Completed (2026-04-21)
- Pass 2: Completed (2026-04-21)
- Pass 3: Completed (2026-04-21)
- Pass 4: Completed (2026-04-21)

## Acceptance focus
- Same-step reinvocation through `dueAt` without graph transition.
- Contract fidelity for call lifecycle payloads.
- Deterministic idempotency key behavior across retries.
