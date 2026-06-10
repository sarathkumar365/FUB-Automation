# Phase 0 — Step-Type Categories: Implementation Notes

> Status: **COMPLETED** (2026-06-10). Decision: [RD-010](../../../repo-decisions/RD-010-step-type-categories.md).

## What landed

- `StepCategory` enum (`CONTROL` / `UTILITY` / `BUSINESS`) in `service.workflow`.
- `category()` added to `WorkflowStepType` as an **abstract** method (not a default) —
  every implementation, including future ones, must declare a category at compile
  time. This is RD-010's "new steps must declare" rule enforced by the compiler,
  stronger than the plan's default-method sketch.
- All 13 step classes tagged per the RD-010 classification table.
- `StepTypeCatalogEntry` DTO + `GET /admin/workflows/step-types` now expose
  `category` (enum name string) — UI palette grouping is unblocked.
- **`StepCategoryBoundaryTest`** — the executable RD-007/RD-010 boundary:
  classpath-scans every `WorkflowStepType` implementation (future steps covered
  automatically, no hand-maintained list) and asserts CONTROL/UTILITY classes have
  no instance field whose type lives in an app package outside
  `service.workflow`. Implemented with Spring's classpath scanner + reflection —
  no new dependency (reuse-first; ArchUnit not added).
- Test-fixture step types (3 anonymous + 1 record) tagged `BUSINESS`.

## Verification

- Full suite: **722 tests, 0 failures** (`./mvnw clean test`, 2026-06-10).
- Boundary test confirmed meaningful: BUSINESS steps (e.g. `fub_create_task` with
  `FollowUpBossClient`, `wait_and_check_communication` with `ProcessedCallRepository`)
  would each violate the kernel rule if reclassified — the assertion is exercised,
  not vacuous.

## Deviations from plan

- `category()` abstract instead of default method (rationale above).
- Boundary check scans **declared instance fields** (constructor-injected
  collaborators) rather than imports — fields are what reflection can see and DI is
  the repo's only dependency vector for steps. Static utility imports would not be
  caught; acceptable for now, noted for the extraction work.
