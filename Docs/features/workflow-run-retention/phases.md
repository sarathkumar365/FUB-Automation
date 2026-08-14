# Workflow run-data retention — phases

Single-phase feature: the design is one coherent vertical slice (migration + config +
repository + service + worker + tests). Splitting it would produce scaffolding-only
increments, which the working agreement discourages.

| Phase | Scope | Status |
|---|---|---|
| 1 | V24 index migration; `workflow.retention.*` properties; `JdbcWorkflowRetentionRepository` batched count/delete; `WorkflowDataRetentionService` (dry-run + purge orchestration); `WorkflowDataRetentionWorker` scheduled shell; tests per plan §Validation | Not started — awaiting owner sign-off on plan.md |

Follow-ups recorded, not in scope:

- `events` table retention — decide once domain-event volume is observed in prod.
- `processed_calls` retention — legacy ledger, low volume.
- Pre-purge archive/export hook — only if a compliance requirement appears.
- Multi-instance leader election for the job — only if deployment ever scales out.
