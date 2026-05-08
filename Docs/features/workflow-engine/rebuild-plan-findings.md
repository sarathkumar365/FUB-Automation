# Workflow Engine Rebuild Plan Review Findings

## Scope
- This document consolidates all findings raised in this chat across multiple senior-level review passes.
- Focus: plan/design issues for the workflow-engine rebuild (not a claim that implementation is complete).

## Wave 1 Reference
- Wave 1 implementation details: [workflow-engine-technical-implementation.md](workflow-engine-technical-implementation.md)

## Status Legend
- **FIXED** — Addressed in Wave 1 code
- **WAVE2** — Deferred to Wave 2+ (not applicable to current scope)
- **DOC** — Documentation/process gap only; no code change needed
- **BY_DESIGN** — Intentional design choice; documented rationale below
- **LOW** — Valid but low-priority; tracked for future consideration

## Consolidated Findings (42)

### A) Architecture and Contract Clarity
1. **[WAVE2]** Trigger contract is inconsistent across docs (`{eventType, filter}` vs plugin-style `{type, config}`). _Trigger system not implemented in Wave 1; column stored but not evaluated._
2. **[WAVE2]** Trigger ownership is duplicated (`automation_workflows.trigger` and `graph.trigger`) with no single source of truth. _Resolve when trigger filtering is built._
3. **[WAVE2]** Trigger filtering is described as out-of-scope in one doc and core behavior in another. _Not yet implemented._
4. **[WAVE2]** `CreateWorkflowRequest.trigger` examples do not consistently match the proposed trigger plugin model. _Resolve alongside #1-3._
5. **[DOC]** Naming drift across docs (`WorkflowEntity/WorkflowRepository` vs `AutomationWorkflowEntity/AutomationWorkflowRepository`) increases handoff friction. _Code uses `AutomationWorkflowEntity` consistently; doc cleanup only._
6. **[DOC]** Planning request contract drifts across docs (some include normalized domain/action/payload hash, some do not). _Wave 1 has concrete `WorkflowPlanRequest` record as the source of truth._
7. **[LOW]** Transition schema is not fully locked as a strict contract (list/terminal object shape is described but not fully formalized). _Validator enforces the shape in code (list = next-nodes, map with `terminal` key = terminal)._
8. **[BY_DESIGN]** No hard contract says every declared result code must have a transition mapping. _Intentional: a step can declare result codes (e.g. TRANSIENT_FAILURE) without requiring transitions for all of them. Unmapped results lead to run failure, which is the correct fallback._
9. **[FIXED]** Terminal transition semantics are ambiguous in parallel/fan-out workflows. _Added PENDING-status guard in `applyTerminalTransition()` — concurrent terminal arrivals are now idempotent; second-to-arrive is a no-op._
10. **[FIXED]** Run-finalization behavior in concurrent terminal paths is not transactionally specified. _Both `applyTerminalTransition()` and `checkRunCompletion()` now guard on `run.getStatus() == PENDING` before finalizing. Combined with `@Version` optimistic locking on the run entity, concurrent finalization attempts are safely rejected._
11. **[LOW]** Run reason-code taxonomy and governance are underspecified (free-form string risk). _Only `"COMPLETED"` and internal error codes used in Wave 1. Revisit if taxonomy grows._
12. **[WAVE2]** No compatibility strategy for schema evolution (`schemaVersion`) and historical workflow execution. _`workflow_graph_snapshot` is frozen at plan time, which is the key safeguard. Evolution policy is a Wave 2 design exercise._
13. **[WAVE2]** No compatibility policy for step-type evolution (changed/removed result codes breaking stored graphs). _Same frozen-snapshot safeguard applies. Step-type registry versioning is Wave 2._

### B) Data Model and Persistence
14. **[FIXED]** `workflow_runs.workflow_id` foreign key is not specified in the migration contract. _Added `CONSTRAINT fk_workflow_runs_workflow FOREIGN KEY (workflow_id) REFERENCES automation_workflows (id)` to V10 migration._
15. **[LOW]** No constraint guidance for `pending_dependency_count` bounds (negative values not explicitly prevented). _Code always initializes >= 0 and decrements. A CHECK constraint would be belt-and-suspenders._
16. **[LOW]** No DB-level invariants for invalid lifecycle transitions (status transition correctness left to code only). _Matches existing policy engine approach (code-enforced). Consistent with codebase patterns._
17. **[LOW]** No canonical key normalization/case policy for workflow keys (risk of duplicate semantics). _Low urgency; can add normalization in service layer or CHECK constraint later._
18. **[WAVE2]** No retention/archival policy for `workflow_runs` and `workflow_run_steps`. _Ops concern, not Wave 1 blocking._
19. **[WAVE2]** No scaling plan for historical tables (partitioning/pruning/index lifecycle) under sustained volume. _Same as #18._
20. **[WAVE2]** No size/redaction policy for `trigger_payload` snapshots (PII and payload bloat risk). _`trigger_payload` is nullable and unused in Wave 1._
21. **[WAVE2]** No explicit limits/compression policy for `workflow_graph_snapshot` and step payload columns. _Low-volume in Wave 1. Revisit under load._

### C) Execution, Reliability, and Operations
22. **[BY_DESIGN]** Idempotency fallback semantics can over-dedupe legitimate events when `eventId` is missing. _The caller controls idempotency key composition via `WorkflowPlanRequest`. If `eventId` is null, the key degrades gracefully. This is intentional — the caller is responsible for providing sufficient uniqueness. Documented separately from legacy policy idempotency which uses domain/action/payload hash._
23. **[BY_DESIGN]** New idempotency composition drifts from legacy semantics if domain/action/payload hash are omitted. _Intentional divergence. Workflow triggers are a different model from policy triggers. The workflow plan request does not carry normalized domain/action because those are policy-engine concepts._
24. **[LOW]** Blocked planning outcomes are not consistently specified as persisted run records (observability gap). _Intentional: BLOCKED = workflow not found, so no run is created. Logging covers observability. Persisting a BLOCKED run record would create orphan rows._
25. **[WAVE2]** Retry classification by result-code substring (e.g., contains `TRANSIENT`) is fragile. _No retry-by-result-code logic exists in Wave 1. `RetryPolicy` record exists as a placeholder. Retry dispatch is Wave 2._
26. **[FIXED]** Worker default-enabled posture increases rollout risk while rebuild is in progress. _Changed `@ConditionalOnProperty` from `matchIfMissing = true` to `matchIfMissing = false`. Worker now requires explicit `workflow.worker.enabled=true` to activate._
27. **[WAVE2]** No explicit backpressure/rate-limit strategy for trigger fan-out (`matching workflows x entities` explosion). _No trigger fan-out exists in Wave 1._
28. **[WAVE2]** No timeout/circuit-breaker/bulkhead policy for outbound steps (`http_request`, Slack, FUB calls). _No outbound steps beyond `wait_and_check_claim` (which uses existing FUB client with retry) in Wave 1._
29. **[WAVE2]** No dead-letter/retry governance for exhausted failures beyond manual retry endpoint behavior. _Stale recovery handles orphaned steps. Exhausted failure governance is Wave 2._
30. **[LOW]** Stale-recovery behavior lacks explicit chaos/fault-injection validation requirements. _Testing concern; not a code gap._
31. **[DOC]** "Zero risk to production" claim is not accurate for shared integrations and dual-engine runtime coexistence. _Doc rhetoric; not a code issue. Accurate statement: "minimal risk via opt-in activation and separate tables."_

### D) Security and Governance
32. **[WAVE2]** No explicit authz/RBAC model for new admin workflow endpoints (CRUD/activate/retry). _Existing policy admin endpoints have the same posture (no RBAC). Platform-wide concern, not workflow-specific. Address when auth layer is added globally._
33. **[WAVE2]** No outbound egress security model for `http_request`/`slack_notify` (allowlist, SSRF controls, secret handling). _These step types don't exist in Wave 1._
34. **[DOC]** No auditability requirement for administrative changes (who created/updated/activated and when). _`created_at`/`updated_at` columns exist. Full audit log (who) is a platform concern._
35. **[DOC]** Repo-wide architecture decision promotion is missing (`Docs/repo-decisions/*` not updated for this shift). _Process gap; track separately._
36. **[DOC]** Feature documentation workflow is not fully conformed for this rebuild track (`Docs/features/<feature-slug>/` lifecycle artifacts, plus explicit phase status updates and policy-aligned validation gates). _Process gap; track separately._

### E) Post-Phase-2 Implementation Findings
37. **[P1]** Activating a newer workflow version can violate unique-active-per-key DB constraint. _Current `AutomationWorkflowService.activate(...)` sets latest version to `ACTIVE` without deactivating prior active version. With index `uk_automation_workflows_active_per_key`, path `v1 ACTIVE -> update creates v2 INACTIVE -> activate v2` can trigger `DataIntegrityViolationException` on save. Fix by deactivating prior active version(s) in scope before activating target version._
38. **[FIXED]** Archiving latest version can hide a key while an older version still routes traffic. _Resolved by key-level deactivation in `AutomationWorkflowService.archive(...)`: service now deactivates all active versions for the key before setting latest version `ARCHIVED`, preventing hidden-active routing._

### F) Post-Phase-3 Implementation Findings
39. **[FIXED]** Deactivate endpoint can report success while workflow key remains active. _Resolved by key-level deactivation in `AutomationWorkflowService.deactivate(...)`: service now deactivates all active versions for the workflow key (not latest-only), so successful deactivation cannot leave older active versions routable._
40. **[FIXED]** Run inspection reports optimistic-lock version as workflow version number. _Resolved in Wave 4a closing fixes: `WorkflowExecutionManager` now writes `workflow_runs.workflow_version` from append-only `automation_workflows.version_number` (fallback `1L`), and both `WorkflowRunQueryService` and `WorkflowStepExecutionService` now use explicit version-number semantic helpers (fallback `1L`) with clarifying comments. Added regression coverage in `WorkflowAdminApiIntegrationTest` asserting real run `workflowVersionNumber` progression `1 -> 2 -> 3`._
41. **[FIXED]** Workflow key normalization is inconsistent between create and read/update paths. _Resolved with centralized key helper usage. Workflow keys now normalize once (trim-only) before duplicate checks, persistence, and key-based reads/writes. `AutomationWorkflowService.create(...)` now persists normalized key value, and lookup/update/lifecycle paths consistently use the same normalization. `WorkflowRunQueryService` and `WorkflowExecutionManager` consume helper-normalized workflow keys for both lookups and idempotency key composition._

### G) Post-Uncommitted Review Reconfirmation (2026-04-15)
42. **[FIXED][RECONFIRMED]** Deactivate endpoint can report success while workflow key remains active. _Reconfirmed by review and now fixed in current working tree by key-level active-version deactivation in `AutomationWorkflowService.deactivate(...)`._
43. **[FIXED][RECONFIRMED]** Archiving latest version can hide a key while an older version still routes traffic. _Reconfirmed by review and now fixed in current working tree by key-level active-version deactivation in `AutomationWorkflowService.archive(...)` before archiving latest._

### H) Post-Wave-4c Review Findings (2026-04-15)
44. **[P1]** Cancel/run-finalization race can overwrite `CANCELED` status. _In `WorkflowStepExecutionService.executeClaimedStep(...)`, run status is checked once before executor call. If an operator cancels the run concurrently after that read, downstream finalization paths can still persist `COMPLETED`/`FAILED` based on stale run state (no optimistic version field on `WorkflowRunEntity`), violating the cancel invariant. Add a fresh run-state recheck/guard before terminal run writes (and/or optimistic concurrency control) in finalization paths._

## Priority Buckets
- `P0`: ~~1, 2, 3,~~ ~~9, 10,~~ ~~22, 23, 25,~~ ~~32, 33~~ — **9, 10 FIXED; rest deferred (WAVE2/BY_DESIGN)**
- `P1`: ~~4, 5, 6, 8, 12, 13,~~ ~~14,~~ ~~18, 20, 27, 28, 29, 31, 34, 35, 36~~, **37, 44,** ~~38, 39, 42, 43~~ — **14/38/39/42/43 FIXED; 37/44 open; rest deferred**
- `P2`: ~~7, 11, 15, 16, 17, 19, 21, 24,~~ ~~26,~~ ~~30, 40, 41~~ — **26, 40, 41 FIXED; rest LOW/deferred**

## Summary of Implemented Actions
| Finding | Status | Change |
|---------|--------|--------|
| #9, #10 | **FIXED** | Added PENDING-status guards in `applyTerminalTransition()` and `checkRunCompletion()` for concurrent terminal race safety |
| #14 | **FIXED** | Added FK constraint `fk_workflow_runs_workflow` in V10 migration |
| #26 | **FIXED** | Flipped worker `matchIfMissing` from `true` to `false` — explicit opt-in required |
| #40 | **FIXED** | Aligned workflow run version semantics to append-only `version_number` at write/read/runtime metadata layers (`WorkflowExecutionManager`, `WorkflowRunQueryService`, `WorkflowStepExecutionService`) and added integration assertions for `workflowVersionNumber == 1/2/3` |
| #41 | **FIXED** | Added shared `KeyNormalizationHelper` and applied it across workflow + policy key operations (including workflow planning idempotency input) so create/read/update/lifecycle paths share one normalization source of truth |

## Wave 4a Closing Validation Added
- Added `WorkflowAdminApiIntegrationTest` wave-gate coverage for:
  - trigger catalog exposure (`webhook_fub`)
  - admin lifecycle create/update/rollback/activate/deactivate/archive
  - in-flight v1 run snapshot immutability after v2 activation
  - run API/detail version assertions (`workflowVersionNumber` progression 1, 2, 3)
  - rollback HTTP consistency (`200 OK`)

## Notes
- This list is intentionally comprehensive and includes both hard technical correctness gaps and process/governance gaps that materially affect delivery quality.
- Recommendation: lock P0 items before expanding beyond current skeleton phases.
- WAVE2-tagged items should be revisited at Wave 2 planning kickoff.
