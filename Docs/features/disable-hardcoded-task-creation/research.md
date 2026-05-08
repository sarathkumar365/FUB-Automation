# Research

## Feature
`disable-hardcoded-task-creation`

## Goal
Stop the legacy "call → task" hardcoded automation from firing in deployed
environments, while preserving local call data (the
`processed_calls` table fills as before). The feature is being retired in
favour of workflow-engine-driven automations; this kill switch is the
"stop the bleeding" step that lets us deploy to Railway without surprise
FUB tasks being created on every qualifying call.

## Current state

- The action path lives in
  [`WebhookEventProcessorService.executeDecision`](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java)
  — when `CallDecisionEngine` returns `CREATE_TASK`, it calls
  `followUpBossClient.createTask(...)` and marks the row `TASK_CREATED`.
- A dev-only guard (`evaluateDevGuard`) already short-circuits this path
  when `SPRING_PROFILES_ACTIVE=local` AND the assigned user doesn't match
  `rules.call-outcome.dev-test-user-id`. Everywhere else (including the
  `prod` profile we're about to use on Railway) the guard is bypassed.
- All of the upstream pipeline (`getCallById`, `persistCallFacts`,
  pre-validation, decision engine) saves call data to `processed_calls`.
  None of that is being removed.

## Decision

Add a property `rules.call-outcome.task-creation-enabled` defaulting to
`true`. Flip it to `false` in `application-prod.properties` so the
deployed Railway environment never fires the action. The existing
`evaluateDevGuard` is renamed to `evaluateActionGuard` and gains a
leading check: if the kill switch is off, return
`TASK_CREATION_DISABLED`, irrespective of profile.

This is a kill switch, not a removal:
- Existing tests don't need to change (they run with the default
  `enabled=true`).
- Local development behaviour stays identical (the local-profile guard
  still fires first when configured; absent that, decision still flows
  through).
- Reverting is a one-line config change.
- The decision/factory/rules code remains in place. A future feature
  (`feature/retire-hardcoded-call-decision-engine`) deletes them once
  workflows have replaced the use case.

## Why a property and not just delete the action

Three reasons:

1. **Reversibility.** If a workflow-based replacement turns out to take
   longer than expected, flipping the env var brings the hardcoded path
   back without a code change.
2. **Test surface stability.** The existing call-flow tests
   (`WebhookProcessingFlowTest`, `WebhookProcessingDevGuardFlowTest`,
   etc.) keep their assertions about `TASK_CREATED` / `SKIPPED` /
   `FAILED` outcomes by leaving the code path intact. A full deletion
   would require rewriting all of them in this same PR — too much.
3. **Clear intent in config.** A reader skimming
   `application-prod.properties` sees explicitly that the task creation
   is off. Deleting the code would silently change behaviour and only
   become apparent by reading the processor source.

## Repo decisions consulted

- `RD-001`, `RD-002`, `RD-003` — domain/event/lead contracts; no
  conflict.
- `RD-004` (admin auth = JWT bearer) — unrelated; no conflict.

No new repo decision is created. Once the hardcoded path is fully
retired in a later feature, that change *will* warrant a repo decision
(it changes the long-term architecture: "all FUB-side automations go
through the workflow engine"). Out of scope here.

## Out of scope

- Deleting `CallDecisionEngine`, `CallbackTaskCommandFactory`, the
  `rules/` package, or any of the dev-guard property scaffolding.
- Removing `dev-test-user-id` (still useful for local dev when the user
  re-enables the kill switch).
- Building the workflow-engine replacement (`fub_fetch_call` step) —
  that's a separate feature.
- Migrating any in-flight `processed_calls` rows; the kill switch only
  affects new events going forward.
