# Workflow Engine Rebuild — Research

## Purpose
This file is the workflow-engine feature research entrypoint required by the repo feature workflow.

## Source Documents Consolidated Here
- [Workflow Engine — Implementation Plan](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine-implementation-plan.md)
- [Workflow Engine — Technical Implementation Details](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine-technical-implementation.md)
- [Workflow Engine Rebuild Plan Review Findings](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine/rebuild-plan-findings.md)
- [Repository Decisions Index](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/repo-decisions/README.md)

## Current Ground Truth (Implementation)
- Wave 1 core runtime exists under `service/workflow/*` with dedicated workflow tables (`V10` migration).
- Wave 2 code is in progress in working tree:
  - expression evaluator + template resolution
  - run context and resolved-config persistence
  - parity step implementations (`wait_and_check_communication`, `fub_reassign`, `fub_move_to_pond`)
  - shared `FubCallHelper`
- Trigger router and workflow builder UI are not implemented yet.

## Risks / Gaps Being Tracked
- Trigger model and routing contracts are deferred to Wave 3.
- Full admin API surface from technical docs is not complete yet (currently create/get/step-types).
- Testcontainers workflow tests are environment dependent (skip when Docker is unavailable).

## Next Research Refresh Point
Refresh this file when Wave 3 trigger routing design is finalized (router contracts, trigger schema, and migration plan from policy path).
