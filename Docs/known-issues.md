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
