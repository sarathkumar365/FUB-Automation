# Workflow Engine Rebuild — Phase 2 Implementation

## Status
In progress

## Goals for This Stabilization Pass
- Finalize Wave 2 in-progress code paths and keep working tree build-clean.
- Enforce explicit opt-in worker defaults.
- Add workflow controller coverage for currently exposed admin endpoints.
- Align feature docs with repo-required structure.

## Implemented in This Pass
- Worker default hardening:
  - `workflow.worker.enabled` now defaults to `false`.
  - `WorkflowWorkerProperties.enabled` default set to `false`.
  - Note: this default was later flipped to `true` during Wave 4 post-close stabilization after rebuild completion.
- New test coverage:
  - `AdminWorkflowControllerTest` with scenarios for create/get/step-types.
  - `WorkflowWorkerPropertiesBindingTest` for default and override binding behavior.
- Documentation alignment:
  - Added `research.md`, `plan.md`, `phases.md`, and phase implementation docs in this folder.

## Wave 2 Code Already Present and Stabilized
- Expression engine and template resolution integration.
- Run context / trigger payload and resolved-config persistence path.
- Parity step implementations and shared `FubCallHelper`.

## Open Items for Next Phase
- Workflow trigger router integration (`WebhookEventProcessorService` still routes assignment to policy engine).
- Additional workflow admin APIs planned in technical design (`PUT`, activate, list, retry).
- Workflow UI module (`ui/src/modules/workflows`).
