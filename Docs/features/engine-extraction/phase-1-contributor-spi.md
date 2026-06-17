# Phase 1 — Convert the 4 code leaks into SPI boundaries (in-place)

> **Status:** `NOT STARTED` · Designed 2026-06-16 (via `/consult`).
> **Implements:** Group 2 tasks 2.1–2.4 of [`phases.md`](./phases.md). **Resolves:** RD-007 open decision 0.4.
> **Companion:** the cross-phase [`migration-runbook.md`](./migration-runbook.md) (Phase 1 section) and [`file-ledger.md`](./file-ledger.md).

## Goal

Remove all **4 code leaks** — where engine classes import FUB business code — by introducing kernel-owned **SPIs** (ports) that the host fills with **adapter** beans. After this phase the engine's `service/workflow/` tree has **zero** imports of `service.person.*`, `service.event.*`, `service.BusinessHoursService`, or `service.workflow.trigger.*`, while behaviour is **byte-for-byte unchanged** for the FUB host.

This is the only phase with runtime-behaviour risk, so it is isolated: **no files move, no packages rename, no module is created** — that is Phase 2. Everything stays under `com.fuba.automation_engine.*` and the full existing test suite stays green throughout.

This is the repo's idiomatic pattern — AGENTS.md mandates "interfaces as ports … favour interface-driven design where extension is expected." The SPIs are ports; the host beans are adapters.

## Decisions locked (from the consult)

1. **Triggers are optional in the engine by default.** The whole trigger policy (required / `.on` shape / domain-event validation) moves into the host `TriggerValidator`; the engine's default no-op accepts anything. A bare engine can `plan()` a triggerless workflow; FUB re-asserts "required" in its adapter. *(Option A.)*
2. **Phase 1 is strictly behaviour-preserving.** Keep `RunContext`'s typed `person`/`now` slots; the `sourcePersonId→subjectId` rename and the generic-contributions shape are **Phase 2**.
3. **`GraphValidationRule` is per-node** (`validateNode(node, nodeId, errors)`) — matches the call site; trigger validation stays separate (workflow-scoped).
4. **Contributors run once per step and must not cache** — encoded in javadoc and a test.
5. **Duplicate contributor keys fail fast** (mirrors `WorkflowStepRegistry`'s duplicate-id guard).
6. **Land as 3 independent commits**, full suite green each; `WorkflowParityTest` is the parity gate.

## The 3 SPIs — new package `service/workflow/spi/`

```java
// RunContextContributor.java — covers leaks #1 + #2
public interface RunContextContributor {
    /** Top-level run-context key this contributor fills, e.g. "person" or "now". Unique across beans. */
    String key();
    /** Resolve this block for the current step build. Invoked once per step; MUST NOT cache; never null. */
    Map<String, Object> contribute(RunContextRequest request);
}

// RunContextRequest.java — neutral fields only (no FUB vocabulary in the contract)
public record RunContextRequest(
        long runId, String workflowKey, String subjectId, String triggerEventId, Instant now) {}

// GraphValidationRule.java — covers leak #3
public interface GraphValidationRule {
    /** Append human-readable errors for this node. Run after the engine's structural validation. */
    void validateNode(Map<String, Object> node, String nodeId, List<String> errors);
}

// TriggerValidator.java — covers leak #4
public interface TriggerValidator {
    /** Empty = valid. Never null. Engine default accepts anything (triggers optional). */
    List<String> validate(Map<String, Object> trigger);
}

// EngineSpiDefaults.java — engine boots with no host
@Configuration
class EngineSpiDefaults {
    @Bean @ConditionalOnMissingBean(TriggerValidator.class)
    TriggerValidator noopTriggerValidator() { return t -> List.of(); }
}
```

> `RunContextContributor` and `GraphValidationRule` need **no** default bean — Spring injects an empty `List<>` when none are registered, which is exactly "the engine ships zero person/now/validation knowledge."

## Order of work — 3 commits on `feature/engine-seams`

The three are independent and can land in any order.

### Commit 1 — `RunContextContributor` (leaks #1, #2)

- **New:** `spi/RunContextContributor`, `spi/RunContextRequest`.
- **Edit `WorkflowStepExecutionService`:** delete imports L10–11; drop the `PersonSnapshotResolver` + `BusinessHoursService` constructor params (L57–58) and fields (L47–48); inject `List<RunContextContributor>`. Replace the person/now block (L227–250) with a keyed loop that fills the **unchanged** typed `RunContext.person`/`now` slots via `getOrDefault`. Add a duplicate-`key()` fail-fast in the constructor.
- **New host adapters** (`service/workflow/contributor/`, stay in `com.fuba.*`):
  - `PersonRunContextContributor` — `key() = "person"`, wraps `PersonSnapshotResolver.resolve(subjectId)`.
  - `NowRunContextContributor` — `key() = "now"`, wraps `BusinessHoursService.isDaytime/hourLocal`.

### Commit 2 — `GraphValidationRule` (leak #3)

- **New:** `spi/GraphValidationRule`.
- **Edit `WorkflowGraphValidator`:** delete import L3; inject `List<GraphValidationRule>`; replace the `validatePersonFieldReferences(...)` call at L117 with a rule loop; **delete** `validatePersonFieldReferences` (L306–333), `collectPersonReferences`, `scanString`, and the two `PERSON_*` patterns (L29–36).
- **New host adapter** (`service/workflow/rule/`): `PersonFieldGraphValidationRule` — the deleted regex/scan logic verbatim, calling `PersonUpsertService.capturedFieldNames()`.

### Commit 3 — `TriggerValidator` (leak #4)

- **New:** `spi/TriggerValidator`, `spi/EngineSpiDefaults`.
- **Edit `AutomationWorkflowService`:** delete import L7; swap the field/param to `TriggerValidator`. **Move the entire trigger policy out** — replace the L281–287 if/else with `errors.addAll(triggerValidator.validate(trigger))` (triggers now optional engine-side). *(Optional cleanup: drop the duplicate step-type check at L289–307 — `WorkflowGraphValidator` already covers it.)*
- **Host wiring:** make `DomainEventTriggerValidator implements TriggerValidator`, owning the `required` / `.on` / domain-event rules its callers expect. It auto-registers and Spring injects it over the no-op default.

## Test plan

- **Update 4 constructors:** `WorkflowStepExecutionServiceTest`, `WorkflowRetryDispatchTest` (pass a fake `List<RunContextContributor>` — e.g. a `now` contributor returning `{isDaytime, hourLocal}`); `AutomationWorkflowServiceTest` (mock `TriggerValidator`); `WorkflowGraphValidatorFieldReferenceTest` (construct with `PersonFieldGraphValidationRule`).
- **New seam tests:**
  - empty contributor list → `person`/`now` resolve to empty in scope;
  - a registered contributor fills its named block;
  - **contributor invoked exactly once per step** (the freshness contract);
  - the rule loop runs every registered `GraphValidationRule`;
  - `TriggerValidator` no-op accepts any trigger; the FUB impl rejects a malformed one.
- **Parity gate:** `WorkflowParityTest` stays green (seeds a person, asserts `fub_reassign`/task end-to-end) — this is the proof that RunContext is unchanged for FUB.

## Non-goals (deferred)

No file moves · no module · no `sourcePersonId→subjectId` rename · no `RunContext` generic-contributions shape · no migration/FK change · no `JacksonConfig` change · no `WorkflowRunListener` observability seam (separate, v0.1-optional). All Phase 2+.

## Risks → mid-flight detection

| Risk | Detection |
|---|---|
| RunContext drifts from today's shape/values | `WorkflowParityTest` red |
| A contributor caches → stale `person`/`now` mid-run | the once-per-step freshness test |
| Two contributors claim the same `key()` | constructor fail-fast at startup |
| Trigger-optional change breaks FUB save validation | `AutomationWorkflowServiceTest` red with `DomainEventTriggerValidator` registered |
| A test still constructs a service with the old business deps | compile failure (grep `new WorkflowStepExecutionService(` first) |

## Validation / definition of done

- `mvn -q -o test` green.
- The Phase 0 census grep returns **zero** business/host-glue imports under `service/workflow/`:
  ```bash
  grep -rnE "service\.(person|event|BusinessHoursService)|service\.workflow\.trigger" \
    src/main/java/com/fuba/automation_engine/service/workflow --include=*.java | grep -v '/contributor/\|/rule/'
  ```
  (expected: empty — the only remaining `controller.dto` import in `WorkflowRunQueryService` is L1, fixed in Phase 2).
- `WorkflowParityTest` green (parity preserved).

## Repo-decisions impact (AGENTS.md mandatory)

Implements RD-007's decoupling mechanism and **resolves RD-007 open decision 0.4** (trigger seam = dedicated `TriggerValidator`, not folded into `GraphValidationRule`). No new repo decision required. Flip RD-007 `Status` toward Accepted once Group 2 lands green.
