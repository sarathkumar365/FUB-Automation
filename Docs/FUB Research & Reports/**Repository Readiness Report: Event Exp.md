**Repository Readiness Report: Event Expansion + Assignment SLA Automation**

**Date**
- April 1, 2026

**Locked Decisions**
- Event onboarding scope: **Catalog + Batch 1** (assignment events first).
- Delayed execution model: **DB-backed worker**.
- Policy storage: **DB + Admin API**.

**Validated Current Platform Areas**
1. **Webhook ingestion platform (reusable)**
- Source-based webhook ingress, signature verification, persistence, async dispatch already exist.
- Key files:
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/controller/WebhookIngressController.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/controller/WebhookIngressController.java)
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java)

2. **Event processing orchestration (needs upgrade)**
- Current processing path is largely call-centric and not domain-routable for broad event support.
- Key file:
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/webhook/WebhookEventProcessorService.java)

3. **Domain modules**
- Call outcome domain exists; assignment domain does not.
- Existing domain reference:
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/rules/CallDecisionEngine.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/rules/CallDecisionEngine.java)

4. **Policy platform (needs build from current)**
- Typed config pattern exists, but persistent runtime policy with admin updates is not present.

5. **Delayed execution platform (needs build from current)**
- Async executor exists, but durable delayed due-check orchestration does not.

6. **FUB adapter layer (needs extension)**
- Port/adapter base exists, but assignment-SLA actions are missing.
- Key files:
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/FollowUpBossClient.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/service/FollowUpBossClient.java)
  - [/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java](/Users/sarathkumar/Projects/2Creative/automation-engine/src/main/java/com/fuba/automation_engine/client/fub/FubFollowUpBossClient.java)

7. **Admin operations platform (reusable patterns, needs assignment surface)**
- Webhook and processed-call admin patterns exist and can be reused for assignment checks/policy.

8. **Reliability/governance constraints to carry forward**
- Parser currently requires `resourceIds[]`; this blocks broad non-call event support.
- Known non-atomic claim risk in processing should not be repeated in new delayed worker flow.

**Agreed Deliverable (V1)**
Build a production-ready **Assignment SLA Automation** capability on top of the existing webhook platform:
1. Generic event catalog + normalized event routing foundation.
2. Assignment domain module with its own lifecycle/status model.
3. DB-backed policy module with admin read/update API.
4. Durable DB-backed delayed due-check worker with atomic claim/retry.
5. FUB adapter extensions for call-check, reassignment, optional audit note.
6. Generic decision contract + assignment decision implementation.
7. Admin operations visibility for policy + assignment checks + replay.
8. Migration/tests/docs updates with staged event rollout.

**5-Phase Platform Roadmap**

| Phase | Main Platform Areas | Outcome |
|---|---|---|
| **Phase 1: Foundation and Contracts** | Webhook ingestion, event orchestration, reliability contracts | Event model becomes domain-ready (not call-only); catalog posture established. |
| **Phase 2: Data and Policy Infrastructure** | Policy platform, reliability governance | Persistent runtime policy control plane is introduced. |
| **Phase 3: Event Expansion + Assignment Triggering** | Ingestion + orchestration + assignment domain | Assignment triggers become first-class routed events with pending checks. |
| **Phase 4: Due Worker + Decision + FUB Actions** | Delayed execution, decision layer, FUB adapter | Durable delayed SLA enforcement with reassign/skip outcomes. |
| **Phase 5: Ops Surface, Hardening, Rollout** | Admin operations, observability/governance | Operators can run, monitor, and replay assignment SLA safely in production. |

**What Is No Longer Valid (Removed from Active Plan)**
- Attempting one-shot support for nearly all FUB events in first delivery.
- 4-phase grouping previously documented.
- Treating async dispatch as equivalent to durable delayed orchestration.

**Final Note**
- This plan keeps existing call-outcome automation intact while expanding platform capability via a modular assignment-first rollout.
