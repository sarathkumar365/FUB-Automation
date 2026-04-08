# Known Issues

This document tracks currently known issues identified in the codebase.

## 1) Non-atomic processing claim can allow duplicate side effects
- Location: `src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java`
- Issue: Processing state transition to `PROCESSING` is not claimed atomically across workers.
- Impact: Duplicate deliveries can be processed concurrently, which may result in duplicate Follow Up Boss reads/writes and duplicated task creation attempts.
- Suggested fix direction: Replace read-then-update with a single DB claim transition (`RECEIVED/RETRYABLE -> PROCESSING`) that only one worker can win.

## 2) Duplicate handling is too broad on DB integrity errors
- Location: `src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java`
- Issue: `DataIntegrityViolationException` during save is treated as duplicate without narrowing to unique-key violations.
- Impact: Non-duplicate integrity problems may be masked, increasing risk of silent data loss.
- Suggested fix direction: Detect and handle only duplicate-key violations as duplicate events; surface other integrity failures explicitly.

## 3) SSE publish path can throw on null event fields
- Location: `src/main/java/com/fuba/automation_engine/service/webhook/live/WebhookSseHub.java`
- Issue: SSE payload is built with `Map.of(...)`, which throws `NullPointerException` for null values (for example, nullable `eventId`).
- Impact: Live feed publish can fail at runtime even when ingestion should continue.
- Suggested fix direction: Build payload using a null-tolerant map implementation (for example `LinkedHashMap`) before sending to subscribers.

## 4) Replay does not reset retry count
- Location: `src/main/java/com/fuba/automation_engine/service/webhook/ProcessedCallAdminService.java`
- Issue: Replay flow resets status and failure fields but leaves `retryCount` unchanged.
- Impact: Replay attempts carry stale retry history, which can make diagnostics and retry behavior misleading.
- Suggested fix direction: Reset `retryCount` to `0` as part of replay reinitialization.

## 5) Missing end-to-end scenario coverage for policy worker execution flow
- Priority: High
- Location: `src/main/java/com/fuba/automation_engine/service/policy/` (worker + execution dispatcher + executors + FUB client integration path)
- Issue: Current tests are mostly unit/integration-at-component level; there is no single scenario-driven test slice that validates the complete flow from claimed DB step to executor dispatch, FUB people fetch, and persisted step/run outcomes.
- Impact: Cross-component regressions in orchestration flow can pass isolated tests but fail in real execution paths.
- Suggested fix direction: Add scenario integration tests (test slices) for policy worker flows, starting with `WAIT_AND_CHECK_CLAIM` success/failure scenarios, including DB claim input, executor selection, external client behavior, and final persistence assertions.

## 6) No watchdog for stale `PROCESSING` policy steps after hard process crashes
- Priority: High
- Location: `src/main/java/com/fuba/automation_engine/service/policy/PolicyExecutionDueWorker.java`
- Issue: Worker now compensates unhandled execution exceptions by failing step/run, but this only covers exceptions observed by the running process. A hard crash (JVM kill/node restart) after atomic claim and before compensation can still leave rows stuck in `PROCESSING`.
- Impact: Rare but possible orphaned executions that require manual DB intervention.
- Prod-readiness note: This remains a production reliability blocker until a stale-`PROCESSING` watchdog/reaper is implemented.
- Suggested fix direction: Add stale-processing recovery (heartbeat/timeout reaper) to requeue or fail `PROCESSING` steps older than threshold with explicit reason code.
