# RD-012: Reporting is a platform layer of auto-discovered providers over a query-port seam

## Status
Proposed (2026-06-19) — **pending two reference implementations.** This architecture is a
**target to extract, not to build first.** It is extracted from the accountability MVP and
the dashboard slices and ratified to **Accepted** in reporting-platform **Phase 3** (rule of
three) — not Phase 1. See [`plan.md`](../features/reporting-platform/plan.md) "Order of work".
Scope: backend `service/reporting/` + `controller/reporting/`; the `ui/` reporting pages
consume the resulting endpoints.

### Sequencing — why this is extracted, not built first
A 2026-06-19 stress-test flagged that committing this contract up front locks in the
abstraction at the moment of least information — in particular the `ReportingQuery` method
granularity (below) and whether one `ReportProvider` shape unifies worklists vs. aggregates
are unanswerable from zero reports. So the build order is: **Phase 1** accountability MVP
and **Phase 2** dashboard as plain, direct vertical slices (own controller + SQL + DTO, no
shared abstraction), then **Phase 3** extracts this framework *from* those two working,
deliberately-different slices and proves it by moving them onto it with no behavior change.
If the two slices turn out to share little worth abstracting, the correct outcome is to
shrink or drop this RD — not to force the seam.

## Context
Reporting is being built MVP-first but is intended to grow into the whole reporting layer
(operational dashboard, lead-lifecycle accountability, task-completion, ad-hoc
natural-language questions). The risk is that each new report becomes a bespoke project —
its own controller, its own ad-hoc SQL, its own re-interpretation of "contacted" /
"called". We want adding a report to be a small, repeatable unit, and we want the heavy
future capture (a FUB CDC mirror; a Vanna text-to-SQL service) to slot in **without**
disturbing reports already shipped.

Data ground truth (what's answerable now vs. later) is in
[`features/reporting-platform/findings/data-model.md`](../features/reporting-platform/findings/data-model.md).
The settled subsystem direction (mirror as capture spine; one read model; deterministic
SQL + Vanna as two query subsystems; live-agent reporting rejected) is in
[`features/reporting-platform/architecture-direction.md`](../features/reporting-platform/architecture-direction.md).

## Decision

**1. The unit of extension is a `ReportProvider`.**
A provider owns one report. It declares a stable `key` (e.g.
`accountability.assigned-vs-called`), accepts shared param types (period / window /
filters), and returns a typed DTO. It contains **no** HTTP, pagination, or persistence
concerns. Adding a report = write a provider + its DTO. Nothing else is hand-wired.

**2. Providers are auto-discovered into a registry** — the same pattern the codebase
already uses for domain-event listeners (`@Component` beans discovered by
`InMemoryDomainEventDispatcher`). A `ReportRegistry` collects all `ReportProvider` beans
at startup, keyed by `key`.

**3. One generic reporting controller** routes `GET /api/reporting/{module}/{report}` to
the registered provider, applying shared param parsing and response envelope. A
per-module controller is permitted only when a report needs a genuinely non-standard
shape — the default is the generic route. *(This is the one sub-decision flagged open at
approval; recorded here as: generic-by-default.)*

**4. Providers read through a `ReportingQuery` port — the load-bearing seam.**
Providers never touch JDBC/JPA or table names directly; they ask the port. Today the port
is backed by a **deterministic-SQL adapter over existing tables** (`persons`,
`processed_calls`, `events`, `workflow_run*`). Later the *same port* is backed by the
CQRS read model fed by the FUB mirror. **Providers do not change when the backing store
evolves.** This is what makes complex reporting additive rather than a rewrite.

**5. Metric semantics live in one shared `definitions` module.**
`Called`, `Contacted`, `Assigned`, `Lead` (and the like) are defined once and reused by
every provider — no report re-decides what "called" means. This is the structural guard
against the definition-trap class of bug (the Run 163 lesson: window/inbound-vs-outbound/
connected-vs-attempt choices silently move the numbers).

**6. Future module types are just new providers — named, not built, here:**
- **Ad-hoc natural language (Vanna):** an `NlQueryProvider` talking to the separate Python
  service through a narrow outbound port. A new provider type; existing reports untouched.
- **Task-completion / "useful" (Q4/Q5/Q6):** new FUB mirror tables → new projection → new
  provider. Additive. The mirror is **not** built in the MVP and is **not** required by it.
- The live-agent ("reporting as an autonomous agent doing live FUB calls") approach is
  **rejected** — see architecture-direction.md.

## Going-forward rule (how to add a report)
1. Define the DTO (the report's response shape).
2. Implement `ReportProvider` (`key`, params, `produce()`), reading only via
   `ReportingQuery` and using the shared `definitions`.
3. If the metric needs a not-yet-available source, add it behind `ReportingQuery` (new
   query method, or — for new signals — a new mirror table + projection), never with
   table access from inside the provider.
4. Reuse the generic controller; add a per-module controller only for a non-standard shape.
5. The UI page consumes `/api/reporting/{module}/{report}`.

## Consequences
- New reports are a provider + DTO, not a project — the explicit goal.
- Reports are insulated from the storage roadmap: existing-tables today, mirror-backed
  read model later, behind one port.
- Deterministic, reviewable SQL backs every fixed metric; numbers are reproducible
  (important for accountability figures a person may contest).
- Definitions are centralized, so "contacted/called" can't drift per report.
- **Open sub-decision (resolve at Phase 1):** the precise `ReportingQuery` method
  granularity (one method per report vs. a small composable query vocabulary). Recorded so
  the choice is deliberate, not accidental.
- **Not decided here:** the text-to-SQL security posture (read-only role, SELECT
  allowlist, SQL validation, audit) — deferred to its own RD when Vanna is picked up.
