# RD-007: Extract the workflow engine as a standalone open-source library ("Camshaft")

## Status
Proposed — intent, boundary, and **name** locked; remaining sub-decisions pending (see [Open decisions](#open-decisions)). Promote to Accepted once Group 0 in [`phases.md`](../features/engine-extraction/phases.md) is closed and the in-place decoupling (Group 2) lands green.

> **2026-06-16 update:** Boundary **re-verified** against current code (the two commits since 2026-06-04 are docs-only). A **4th code leak** was found and the **name was decided**. See the executable [`migration-runbook.md`](../features/engine-extraction/migration-runbook.md) + [`file-ledger.md`](../features/engine-extraction/file-ledger.md).

## Context
The workflow engine (`service/workflow/**`) has matured through Waves 1–3 and the domain-events feature (Phases 0–5). A code-verified boundary audit (2026-06-04, see [`engine-extraction/README.md`](../features/engine-extraction/README.md)) confirms the engine is cleanly separable from the Follow Up Boss (FUB) business app:

- Only **4 files** outside `service/workflow/` import from inside it.
- The workflow JPA entities have **zero** ORM relationships to business entities.
- All domain coupling is at the edges (steps, triggers), not in the kernel.

Total coupling to undo (re-verified 2026-06-16): **4 code leaks** — `PersonSnapshotResolver`, `BusinessHoursService` (both in `WorkflowStepExecutionService`), `validatePersonFieldReferences`→`PersonUpsertService` (`WorkflowGraphValidator`), and **`AutomationWorkflowService`→`DomainEventTriggerValidator`** (newly found; `validate()` also hard-requires a trigger) — plus **2 schema FK leaks** (`workflow_runs.webhook_event_id`→`webhook_events`, `.domain_event_id`→`events`) + minor layering/packaging/naming items. Steps and triggers are host glue, not engine.

We want to showcase this as an open-source artifact: a domain-agnostic, embeddable workflow engine where users supply their own step types.

## Decision
Extract the engine into a separate open-source repository.

**Locked:**
- **Name:** `Camshaft` (decided 2026-06-16; supersedes the earlier `Inline`). Thesis: a camshaft drives a *programmed sequence of timed steps* — which is exactly what the engine does (a scheduled worker meters durable steps forward in order). Artifact `camshaft-engine`, namespace `io.github.<handle>.camshaft.*`. Still distinct from RD-005's **product** brand "Throughline". *(Earlier candidates Inline/Tenon/Relay/Penstock dropped.)*
- **License:** Apache 2.0 (matches Spring/Jackson ecosystem expectations; explicit patent grant).
- **v0.1.0 scope:** Spring Boot 4.0.3 / Java 21, JPA + Flyway + scheduled worker, shipped as-is.
- **v0.2.0 roadmap:** split `camshaft-core` (pure-Java SPI + execution, persistence behind a `RunStore` port) from `camshaft-spring` (Boot starter), enabling Quarkus/Micronaut/plain-JVM hosts.
- **Public entry point:** `WorkflowExecutionManager.plan(WorkflowPlanRequest)`. Triggers (webhook routers, crons, etc.) are the host's responsibility, not the engine's.
- **Decoupling mechanism:** **three** SPIs (the audit found the 4th leak needs its own seam) — `RunContextContributor` (host fills `person`/`now`/etc. into run scope; covers leaks #1+#2), `GraphValidationRule` (host plugs domain-specific per-node save-time validation; leak #3), and `TriggerValidator` (host plugs workflow-scoped trigger-config validation; leak #4) — plus a `sourcePersonId → subjectId` rename across the public step SPI that must land before v0.1. Introduced **in-place in this repo first** (runbook Phase 1), proven by the existing test suite, then moved out.

## Consequences
- The FUB app becomes the first *consumer* of `camshaft-engine` (migration is a separate, later effort — out of scope here).
- Steps and triggers stay private; a curated set of 5 generic steps ship as OSS examples.
- The engine's schema drops its 2 FK constraints to business tables; correlation columns become opaque ids.
- Maintaining a clean public boundary becomes an ongoing constraint on the FUB app (a feature, not a cost).

## Resolved 2026-06-16
- **Name:** `Camshaft` (was open; see Decision).
- **Trigger-validation seam:** a **dedicated `TriggerValidator`** SPI, *not* `GraphValidationRule`. The audit confirmed trigger validation is workflow-scoped (it validates the whole trigger map at save time) while `GraphValidationRule` is per-node — they don't fit. `AutomationWorkflowService`'s `validate()` also hard-requires a trigger; the engine makes trigger-presence host-policy.
- **Observability:** the durable run/step record + `WorkflowRunQueryService` read API stay **in** the engine (its system of record). A thin `WorkflowRunListener` lifecycle-hook SPI is the seam for metrics/tracing (host adapts to Micrometer/OTel); no vendor bundled. Recommended for v0.1.
- **Replay:** durable resume/retry/crash-recovery is already core. Deterministic *re-run of a finished run* from the persisted `workflow_graph_snapshot` + `trigger_payload` is engine-appropriate but scoped to **v0.2**. The FUB event-replay harness (`replay/`) is host-only.

## Open decisions
Tracked as Group 0 in [`phases.md`](../features/engine-extraction/phases.md):
- GitHub handle/org for the Maven namespace (`io.github.<handle>.camshaft.*`) vs. a purchased domain.
- Single `camshaft-engine` artifact vs. early `-core`/`-spring` split for v0.1.
- `domain_event_id` column: keep as opaque correlation id, or drop from the OSS schema.
- Repo visibility (public day 1 vs. private until polished).

## References
- Boundary audit & inside/outside lists: [`engine-extraction/README.md`](../features/engine-extraction/README.md)
- Pre-lift checklist: [`engine-extraction/phases.md`](../features/engine-extraction/phases.md)
- Product naming (distinct): [`RD-005`](./RD-005-product-name-throughline.md)
