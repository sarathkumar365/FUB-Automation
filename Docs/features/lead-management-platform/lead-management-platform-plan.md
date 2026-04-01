# Lead Management Platform Plan

## Intent
Build a lead-management-ready automation foundation that supports:
- internal lead inflow
- external lead inflow (FUB now, additional CRMs later)

V1 delivery remains assignment-SLA-first while keeping architecture source-agnostic.

## Locked Decisions
- Event onboarding scope: Catalog + Batch 1 (assignment events first)
- Delayed execution model: DB-backed worker
- Policy storage: DB + Admin API

## Prework (must complete before implementation)
- Reframe contracts from provider-centric to lead-domain terms.
- Standardize normalized event fields:
  - `sourceSystem`
  - `sourceLeadId`
  - internal lead identity mapping reference
- Keep provider-specific transport details inside adapter implementations only.

## Validated Platform Upgrade Areas
1. Webhook ingestion platform: reusable, needs event-shape generalization.
2. Event orchestration: needs domain-aware routing beyond call-centric flow.
3. Domain modules: call domain exists; assignment domain is new.
4. Policy platform: needs persistent runtime policy control.
5. Delayed execution platform: async exists; durable due-check worker is new.
6. Adapter layer: base FUB adapter exists; assignment-SLA operations need extension.
7. Admin ops platform: reusable patterns exist; assignment surfaces are new.
8. Reliability governance: enforce atomic claim and stronger idempotent processing in new flow.

## 5-Phase Roadmap
| Phase | Main Platform Areas | Outcome |
|---|---|---|
| Phase 1: Foundation and Contracts | Ingestion, orchestration contracts, reliability boundaries | Domain-ready event model and catalog posture are established. |
| Phase 2: Data and Policy Infrastructure | Policy platform, reliability governance | Persistent runtime policy control plane is introduced. |
| Phase 3: Event Expansion + Assignment Triggering | Ingestion + orchestration + assignment domain | Assignment events create pending checks through routed handlers. |
| Phase 4: Due Worker + Decision + Adapter Actions | Delayed execution, decision layer, adapter extensions | Durable delayed SLA enforcement with reassign/skip outcomes. |
| Phase 5: Ops Surface, Hardening, Rollout | Admin operations, observability/governance | Safe operations flow (monitoring, replay, policy control) is production-ready. |
