# Decisions and Feature Phases

## Decision Alignment (Implemented)

Three repo-wide decisions govern the platform:

| Decision | Summary | Code Anchor |
|----------|---------|-------------|
| **RD-001** | Source-agnostic normalized event contract. All webhook sources produce `NormalizedWebhookEvent` records with uniform fields. | `NormalizedWebhookEvent.java` |
| **RD-002** | Event catalog with explicit state-controlled routing. Each `(source, eventType)` maps to a `supportState` (`SUPPORTED`, `STAGED`, `IGNORED`) that gates dispatch. | `StaticWebhookEventSupportResolver.java` |
| **RD-003** | Lead identity mapping boundary. Currently deferred — runtime uses `sourceLeadId` directly. Identity resolver contract removed in migration `V8`. | `V8__remove_identity_resolver_contract.sql` |

---

## Feature Phase State

All phases are **completed**:

| Phase | Focus | Status |
|-------|-------|--------|
| Sprint 0 | RFC lock gate (3 RFCs approved) | Completed |
| Phase 1 | Normalized event contract, catalog resolver, domain routing | Completed |
| Phase 2 | Policy control plane with optimistic concurrency | Completed |
| Phase 3 | Assignment event expansion, policy execution runtime tables | Completed |
| Phase 4 | Due worker, claim/communication executors, transition engine | Completed |
| Phase 5 | Action executor, admin execution APIs, expanded admin surfaces | Completed |
| Phase 6 | Stale `PROCESSING` watchdog/reaper | Completed |
| Phase 7 | Action target contract + log-only action execution | Completed |

**Current runtime state:** Assignment flow is in planning + execution mode. Due worker claims and executes steps. Action step validates required targets and executes through log-only adapter methods, returning `ACTION_SUCCESS` in dev mode.
