# Decisions and Feature Phases

> ⚠️ **Staleness banner (2026-06-03).** This deep-dive set predates the **Lead→Person rename (V21)** and the **domain-events feature** (typed domain events / Rail 2, the `events` table V22, `workflow_runs.domain_event_id` V23, the engine-echo gate, and run-supersede). Where it describes webhooks triggering workflows directly, a `leads` table / `sourceLeadId`, a 5-method FUB client, or the legacy policy engine as live, treat it as historical. Current sources: [`../features/domain-events/README.md`](../features/domain-events/README.md), [`../features/domain-events/README.md`](../features/domain-events/README.md), and the latest Flyway migrations (V23). A full content refresh of this set is pending.

## Decision Alignment (Implemented)

Three repo-wide decisions govern the platform:

| Decision | Summary | Code Anchor |
|----------|---------|-------------|
| **RD-001** | Source-agnostic normalized event contract. All webhook sources produce `NormalizedWebhookEvent` records with uniform fields. | `NormalizedWebhookEvent.java` |
| **RD-002** | Event catalog with explicit state-controlled routing. Each `(source, eventType)` maps to a `supportState` (`SUPPORTED`, `STAGED`, `IGNORED`) that gates dispatch. | `StaticWebhookEventSupportResolver.java` |
| **RD-003** | Lead identity mapping boundary. Currently deferred — runtime uses `sourceLeadId` directly. Identity resolver contract removed in migration `V8`. | `V8__remove_identity_resolver_contract.sql` |

---

## Historical Feature Phases (policy subsystem — superseded)

> The phases below describe the **original policy subsystem** that was dropped in V12. They are kept as a historical record of how the platform first evolved. Active feature work is now tracked under [`Docs/features/`](../features/) — see in particular the workflow engine and domain-events feature folders.

| Phase | Focus | Status |
|-------|-------|--------|
| Sprint 0 | RFC lock gate (3 RFCs approved) | Completed (historical) |
| Phase 1 | Normalized event contract, catalog resolver, domain routing | Completed (still in use) |
| Phase 2 | Policy control plane with optimistic concurrency | Superseded by V12 removal |
| Phase 3 | Assignment event expansion, policy execution runtime tables | Superseded by V12 removal |
| Phase 4 | Due worker, claim/communication executors, transition engine | Superseded by V12 removal |
| Phase 5 | Action executor, admin execution APIs, expanded admin surfaces | Superseded by V12 removal |
| Phase 6 | Stale `PROCESSING` watchdog/reaper | Superseded by V12 removal |
| Phase 7 | Action target contract + log-only action execution | Superseded by V12 removal |

**Current runtime state:** the policy subsystem has been removed. Webhook-driven automation now flows through the workflow engine (`automation_workflows` / `workflow_runs` / `workflow_run_steps`), with the trigger router dispatching to active workflows after `WebhookEventProcessorService` upserts the affected entity.
