# Known Issues

This document tracks currently known issues identified in the codebase.

**Last reviewed:** 2026-04-08

| # | Issue | Priority | Status |
|---|-------|----------|--------|
| 1 | Non-atomic processing claim can allow duplicate side effects | Medium | Open |
| 2 | Duplicate handling is too broad on DB integrity errors | Medium | Open |
| 3 | SSE publish path can throw on null event fields | Medium | Open |
| 4 | Replay does not reset retry count | Low | Open |
| 5 | Missing end-to-end scenario coverage for policy worker execution flow | High | Open |
| 6 | No watchdog for stale `PROCESSING` policy steps after hard crashes | High | Open — production reliability blocker |

---

## 1) Non-atomic processing claim can allow duplicate side effects

- **Status:** Open
- **Location:** `service/webhook/WebhookEventProcessorService.java`
- **Issue:** Processing state transition to `PROCESSING` is not claimed atomically across workers.
- **Impact:** Duplicate deliveries can be processed concurrently, which may result in duplicate Follow Up Boss reads/writes and duplicated task creation attempts.
- **Suggested fix:** Replace read-then-update with a single DB claim transition (`RECEIVED/RETRYABLE → PROCESSING`) that only one worker can win.

## 2) Duplicate handling is too broad on DB integrity errors

- **Status:** Open
- **Location:** `service/webhook/WebhookIngressService.java`
- **Issue:** `DataIntegrityViolationException` during save is treated as duplicate without narrowing to unique-key violations.
- **Impact:** Non-duplicate integrity problems may be masked, increasing risk of silent data loss.
- **Suggested fix:** Detect and handle only duplicate-key violations as duplicate events; surface other integrity failures explicitly.

## 3) SSE publish path can throw on null event fields

- **Status:** Open
- **Location:** `service/webhook/live/WebhookSseHub.java`
- **Issue:** SSE payload is built with `Map.of(...)`, which throws `NullPointerException` for null values (e.g. nullable `eventId`).
- **Impact:** Live feed publish can fail at runtime even when ingestion should continue.
- **Suggested fix:** Build payload using a null-tolerant map (e.g. `LinkedHashMap`) before sending to subscribers.

## 4) Replay does not reset retry count

- **Status:** Open
- **Location:** `service/webhook/ProcessedCallAdminService.java`
- **Issue:** Replay flow resets status and failure fields but leaves `retryCount` unchanged.
- **Impact:** Replay attempts carry stale retry history, making diagnostics and retry behavior misleading.
- **Suggested fix:** Reset `retryCount` to `0` as part of replay reinitialization.

## 5) Missing end-to-end scenario coverage for policy worker execution flow

- **Status:** Open
- **Priority:** High
- **Location:** `service/policy/` (worker + execution dispatcher + executors + FUB client integration path)
- **Issue:** Current tests are mostly unit/component-level; no single scenario-driven test slice validates the complete flow from claimed DB step → executor dispatch → FUB people fetch → persisted step/run outcomes.
- **Impact:** Cross-component regressions in orchestration flow can pass isolated tests but fail in real execution paths.
- **Suggested fix:** Add scenario integration tests for policy worker flows starting with `WAIT_AND_CHECK_CLAIM` success/failure, including DB claim input, executor selection, external client behavior, and final persistence assertions.

## 6) No watchdog for stale `PROCESSING` policy steps after hard crashes

- **Status:** Open — **production reliability blocker**
- **Priority:** High
- **Location:** `service/policy/PolicyExecutionDueWorker.java`
- **Issue:** Worker compensates unhandled execution exceptions, but only covers exceptions observed by the running process. A hard crash (JVM kill/node restart) after atomic claim and before compensation can leave rows stuck in `PROCESSING` indefinitely.
- **Impact:** Orphaned executions require manual DB intervention to recover.
- **Suggested fix:** Add stale-processing recovery (heartbeat/timeout reaper) to requeue or fail `PROCESSING` steps older than a configurable threshold with an explicit reason code (`STALE_PROCESSING_RECOVERED`).
