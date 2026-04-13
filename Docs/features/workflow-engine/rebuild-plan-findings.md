# Workflow Engine Rebuild Plan Review Findings

## Scope
- This document consolidates all findings raised in this chat across multiple senior-level review passes.
- Focus: plan/design issues for the workflow-engine rebuild (not a claim that implementation is complete).

## Wave 1 Reference
- Wave 1 implementation details: [workflow-engine-technical-implementation.md](/Users/sarathkumar/Projects/2Creative/automation-engine/Docs/features/workflow-engine-technical-implementation.md)

## Consolidated Findings (36)

### A) Architecture and Contract Clarity
1. Trigger contract is inconsistent across docs (`{eventType, filter}` vs plugin-style `{type, config}`).
2. Trigger ownership is duplicated (`automation_workflows.trigger` and `graph.trigger`) with no single source of truth.
3. Trigger filtering is described as out-of-scope in one doc and core behavior in another.
4. `CreateWorkflowRequest.trigger` examples do not consistently match the proposed trigger plugin model.
5. Naming drift across docs (`WorkflowEntity/WorkflowRepository` vs `AutomationWorkflowEntity/AutomationWorkflowRepository`) increases handoff friction.
6. Planning request contract drifts across docs (some include normalized domain/action/payload hash, some do not).
7. Transition schema is not fully locked as a strict contract (list/terminal object shape is described but not fully formalized).
8. No hard contract says every declared result code must have a transition mapping.
9. Terminal transition semantics are ambiguous in parallel/fan-out workflows.
10. Run-finalization behavior in concurrent terminal paths is not transactionally specified.
11. Run reason-code taxonomy and governance are underspecified (free-form string risk).
12. No compatibility strategy for schema evolution (`schemaVersion`) and historical workflow execution.
13. No compatibility policy for step-type evolution (changed/removed result codes breaking stored graphs).

### B) Data Model and Persistence
14. `workflow_runs.workflow_id` foreign key is not specified in the migration contract.
15. No constraint guidance for `pending_dependency_count` bounds (negative values not explicitly prevented).
16. No DB-level invariants for invalid lifecycle transitions (status transition correctness left to code only).
17. No canonical key normalization/case policy for workflow keys (risk of duplicate semantics).
18. No retention/archival policy for `workflow_runs` and `workflow_run_steps`.
19. No scaling plan for historical tables (partitioning/pruning/index lifecycle) under sustained volume.
20. No size/redaction policy for `trigger_payload` snapshots (PII and payload bloat risk).
21. No explicit limits/compression policy for `workflow_graph_snapshot` and step payload columns.

### C) Execution, Reliability, and Operations
22. Idempotency fallback semantics can over-dedupe legitimate events when `eventId` is missing.
23. New idempotency composition drifts from legacy semantics if domain/action/payload hash are omitted.
24. Blocked planning outcomes are not consistently specified as persisted run records (observability gap).
25. Retry classification by result-code substring (e.g., contains `TRANSIENT`) is fragile.
26. Worker default-enabled posture increases rollout risk while rebuild is in progress.
27. No explicit backpressure/rate-limit strategy for trigger fan-out (`matching workflows x entities` explosion).
28. No timeout/circuit-breaker/bulkhead policy for outbound steps (`http_request`, Slack, FUB calls).
29. No dead-letter/retry governance for exhausted failures beyond manual retry endpoint behavior.
30. Stale-recovery behavior lacks explicit chaos/fault-injection validation requirements.
31. “Zero risk to production” claim is not accurate for shared integrations and dual-engine runtime coexistence.

### D) Security and Governance
32. No explicit authz/RBAC model for new admin workflow endpoints (CRUD/activate/retry).
33. No outbound egress security model for `http_request`/`slack_notify` (allowlist, SSRF controls, secret handling).
34. No auditability requirement for administrative changes (who created/updated/activated and when).
35. Repo-wide architecture decision promotion is missing (`Docs/repo-decisions/*` not updated for this shift).
36. Feature documentation workflow is not fully conformed for this rebuild track (`Docs/features/<feature-slug>/` lifecycle artifacts, plus explicit phase status updates and policy-aligned validation gates).

## Priority Buckets
- `P0`: 1, 2, 3, 9, 10, 22, 23, 25, 32, 33
- `P1`: 4, 5, 6, 8, 12, 13, 14, 18, 20, 27, 28, 29, 31, 34, 35, 36
- `P2`: 7, 11, 15, 16, 17, 19, 21, 24, 26, 30

## Notes
- This list is intentionally comprehensive and includes both hard technical correctness gaps and process/governance gaps that materially affect delivery quality.
- Recommendation: lock P0 items before expanding beyond current skeleton phases.
