# Wave 5 — Pass 1 Implementation (Contract Freeze + Assignment Runtime Cutover)

## Status
Completed

## Date
2026-04-16

## Behavior Changes Delivered
- Assignment-domain webhook processing no longer creates policy execution plans/runs.
- Assignment-domain processing remains non-blocking and hands off to workflow trigger routing.
- Call-domain and unknown-domain processing behavior remains unchanged.
- Router failure isolation remains unchanged: routing exceptions are logged and do not fail webhook processing.

## Code Changes (Pass 1)
- `WebhookEventProcessorService`:
  - Removed `PolicyExecutionManager` dependency from this service.
  - Removed assignment policy planning loop/request construction.
  - Kept assignment logging and empty-resource guardrails.
- `WebhookEventProcessorServiceTest`:
  - Reworked assignment tests to assert workflow-routing-only behavior.
  - Added explicit no-policy-planning behavior coverage for assignment events with resource IDs.
- `WorkflowTriggerEndToEndTest`:
  - Removed side-by-side policy-flow assumptions and policy seeding dependency.
  - Retained workflow run creation/execution assertions.

## Validation
- `./mvnw test -Dtest='WebhookEventProcessorServiceTest,WorkflowTriggerEndToEndTest'`
  - Result: passed (`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`)
- `./mvnw test`
  - Result: passed (`Tests run: 406, Failures: 0, Errors: 0, Skipped: 0`)

## Handoff Notes (Pass 2+)
- Pass 2 should disconnect policy admin APIs and policy UI route/nav.
- Pass 3+ will cover policy-table drop migration and broader policy module/test teardown.
