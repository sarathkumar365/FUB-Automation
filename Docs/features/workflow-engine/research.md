# Workflow Engine Rebuild — Research

## Purpose
This file is the workflow-engine feature research entrypoint required by the repo feature workflow.

## Source Documents Consolidated Here
- [Workflow Engine — Implementation Plan](workflow-engine-implementation-plan.md)
- [Workflow Engine — Technical Implementation Details](workflow-engine-technical-implementation.md)
- [Workflow Engine Rebuild Plan Review Findings](rebuild-plan-findings.md)
- [Repository Decisions Index](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/repo-decisions/README.md)

## Current Ground Truth (Implementation)
- Wave 1 core runtime exists under `service/workflow/*` with dedicated workflow tables (`V10` migration).
- Wave 2 stabilization scope is completed:
  - expression evaluator + template resolution
  - run context and resolved-config persistence
  - parity step implementations (`wait_and_check_communication`, `fub_reassign`, `fub_move_to_pond`)
  - shared `FubCallHelper`
- Wave 3 work in working tree now includes:
  - retry primitive (`StepExecutionResult` transient flag + retry dispatch)
  - trigger plugin infrastructure (`WorkflowTriggerType`, `TriggerMatchContext`, `EntityRef`)
  - `FubWebhookTriggerType`
  - `WorkflowTriggerRouter` integrated into `WebhookEventProcessorService`
  - MVP step library (`fub_add_tag`, `http_request`, `slack_notify`)
  - Phase 4 end-to-end proof test harness (`WorkflowTriggerEndToEndTest`)

## Risks / Gaps Being Tracked
- Trigger model/router API authoring in admin endpoints is still deferred.
- Retry control APIs are deferred (run-level retry, step-level retry).
- Workflow builder UI module is deferred (`ui/src/modules/workflows`).
- Testcontainers workflow tests are environment dependent (skip when Docker is unavailable).
- `webhookEventId` propagation into workflow runs remains deferred (router-planned runs keep `null`).
- `fub_add_tag` remains log-only in Wave 3 by design.
- `http_request`/`slack_notify` egress allowlist/SSRF hardening is deferred.

## Next Research Refresh Point
Refresh at Wave 4 planning kickoff (expanded step library hardening + UI workflow builder integration).
