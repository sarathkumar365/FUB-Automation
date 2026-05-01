# Phase 6 Implementation Log

Status: Completed (Step 1 completed)

## Scope
- Implement stale `PROCESSING` watchdog/reaper for policy execution reliability.
- Keep lease source as `policy_execution_steps.updated_at`.
- Apply bounded redrive behavior: requeue once, then fail deterministically.

## Step Plan
1. Implement stale-processing recovery in persistence + worker + service layers
   - Status: Completed (2026-04-08)
   - Added DB column:
     - `policy_execution_steps.stale_recovery_count` (default `0`, non-null)
   - Added worker config:
     - `policy.worker.stale-processing-enabled` (default `true`)
     - `policy.worker.stale-processing-timeout-minutes` (default `15`)
     - `policy.worker.stale-processing-requeue-limit` (default `1`)
     - `policy.worker.stale-processing-batch-size` (default `50`)
   - Added stale recovery repository contract and JDBC implementation:
     - query stale `PROCESSING` rows by `updated_at <= staleBefore`
     - use `FOR UPDATE SKIP LOCKED` for multi-worker safety
     - requeue rows when `stale_recovery_count < requeueLimit`:
       - `PROCESSING -> PENDING`
       - `due_at = now`
       - `stale_recovery_count += 1`
       - clear transient fields (`result_code`, `error_message`)
     - fail rows when `stale_recovery_count >= requeueLimit`:
       - `PROCESSING -> FAILED`
       - deterministic error message
   - Wired stale recovery pass at worker poll start (before due-step claim loop).
   - Added service reconciliation:
     - fail parent run for stale-failed rows with reason code `STALE_PROCESSING_TIMEOUT`
     - idempotent handling for terminal runs.

## Validation
- Targeted suites:
  - `./mvnw test -Dtest=PolicyWorkerPropertiesBindingTest,PolicyExecutionDueWorkerTest,PolicyStepExecutionServiceTest,PolicyExecutionStepClaimRepositoryPostgresTest`
  - Result: pass (`28` tests, `0` failures, `0` errors, `4` skipped when Docker/Testcontainers unavailable)
- Full backend suite:
  - `./mvnw test`
  - Result: pass (`252` tests, `0` failures, `0` errors, `10` skipped)

## Notes for Next Agent
- Stale watchdog uses `updated_at` as lease age source; no heartbeat column exists in this increment.
- Requeue timing is immediate (`due_at = now`) to maximize recovery speed and operator visibility.
- Run-level stale timeout failures are exposed via `reason_code=STALE_PROCESSING_TIMEOUT`.
