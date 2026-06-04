# RD-007: Extract the workflow engine as a standalone open-source library ("Inline")

## Status
Proposed — intent and boundary locked; several sub-decisions pending (see [Open decisions](#open-decisions)). Promote to Accepted once Group 0 in [`phases.md`](../features/engine-extraction/phases.md) is closed and the in-place decoupling (Group 2) lands green.

## Context
The workflow engine (`service/workflow/**`) has matured through Waves 1–3 and the domain-events feature (Phases 0–5). A code-verified boundary audit (2026-06-04, see [`engine-extraction/README.md`](../features/engine-extraction/README.md)) confirms the engine is cleanly separable from the Follow Up Boss (FUB) business app:

- Only **4 files** outside `service/workflow/` import from inside it.
- The workflow JPA entities have **zero** ORM relationships to business entities.
- All domain coupling is at the edges (steps, triggers), not in the kernel.

Total coupling to undo: **3 code leaks** (`PersonSnapshotResolver`, `BusinessHoursService`, `validatePersonFieldReferences`→`PersonUpsertService`) + **2 schema FK leaks** (`workflow_runs.webhook_event_id`→`webhook_events`, `.domain_event_id`→`events`) + minor layering/packaging/naming items. Steps and triggers are host glue, not engine.

We want to showcase this as an open-source artifact: a domain-agnostic, embeddable workflow engine where users supply their own step types.

## Decision
Extract the engine into a separate open-source repository.

**Locked:**
- **Name:** `Inline` — thesis-word naming (the engine runs *inline* with the host app, not as a separate cluster/orchestrator process). This is the **engine library** name, distinct from RD-005's **product** brand "Throughline"; the two intentionally differ (one is an internal product, the other a public library).
- **License:** Apache 2.0 (matches Spring/Jackson ecosystem expectations; explicit patent grant).
- **v0.1.0 scope:** Spring Boot 4.0.3 / Java 21, JPA + Flyway + scheduled worker, shipped as-is.
- **v0.2.0 roadmap:** split `inline-core` (pure-Java SPI + execution, persistence behind a `RunStore` port) from `inline-spring` (Boot starter), enabling Quarkus/Micronaut/plain-JVM hosts.
- **Public entry point:** `WorkflowExecutionManager.plan(WorkflowPlanRequest)`. Triggers (webhook routers, crons, etc.) are the host's responsibility, not the engine's.
- **Decoupling mechanism:** two SPIs — `RunContextContributor` (host fills `person`/`now`/etc. into run scope) and `GraphValidationRule` (host plugs domain-specific save-time validation). Introduced **in-place in this repo first**, proven by the existing test suite, then copied out.

## Consequences
- The FUB app becomes the first *consumer* of `inline-engine` (migration is a separate, later effort — out of scope here).
- Steps and triggers stay private; a curated set of 5 generic steps ship as OSS examples.
- The engine's schema drops its 2 FK constraints to business tables; correlation columns become opaque ids.
- Maintaining a clean public boundary becomes an ongoing constraint on the FUB app (a feature, not a cost).

## Open decisions
Tracked as Group 0 in [`phases.md`](../features/engine-extraction/phases.md):
- GitHub handle/org for the Maven namespace (`io.github.<handle>.inline.*`) vs. a purchased domain.
- Single `inline-engine` artifact vs. early `-core`/`-spring` split for v0.1.
- `domain_event_id` column: keep as opaque correlation id, or drop from the OSS schema.
- Trigger-validation seam: reuse `GraphValidationRule`, or a dedicated `TriggerValidator` hook.
- Repo visibility (public day 1 vs. private until polished).

## References
- Boundary audit & inside/outside lists: [`engine-extraction/README.md`](../features/engine-extraction/README.md)
- Pre-lift checklist: [`engine-extraction/phases.md`](../features/engine-extraction/phases.md)
- Product naming (distinct): [`RD-005`](./RD-005-product-name-throughline.md)
