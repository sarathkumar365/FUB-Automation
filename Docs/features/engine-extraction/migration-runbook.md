# Engine Extraction — Migration Runbook (executable)

> **Re-verified 2026-06-16** against current code. Companion to [`file-ledger.md`](./file-ledger.md) and [`RD-007`](../../repo-decisions/RD-007-engine-as-standalone-library.md).
> Every file path + line number below was cross-checked against HEAD.

**Name locked: `camshaft`** — artifact `camshaft-engine`, base package `io.github.<handle>.camshaft.*` (thesis: a camshaft drives a *programmed sequence of timed steps*, exactly what this engine does). The only remaining placeholder is `<handle>` — your GitHub handle/org for the Maven namespace `io.github.<handle>`. Confirm the Maven Central coordinates + GitHub repo name are free before publishing.

**Target:** standalone OSS Maven module `camshaft-engine`, base package `io.github.<handle>.camshaft.*`, Apache-2.0, Spring Boot 4.0.3 / Java 21.

**Shape of the work.** The Java *import* boundary is genuinely clean — only **4 code seams** stand between the kernel and a clean cut. But "clean lift" understates four things the import audit doesn't show: Spring **bean-conflict + auto-config** wiring (G1, G2), the **test story** (G3, G4, G7), a real **Flyway baseline authoring** task (G5), and an **incomplete SPI surface** (G6 — it's 3 SPIs **plus** a rename). Those are folded into the phases below as gap items `G1…G15`.

---

## The coupling inventory (what must be undone)

### 4 code leaks — engine files reaching into business code

| # | File | Coupling (verified line) | Fix |
|---|------|--------------------------|-----|
| 1 | `WorkflowStepExecutionService` | imports `service.person.PersonSnapshotResolver` (L11); builds `RunContext.person` at L233 | `RunContextContributor` SPI |
| 2 | `WorkflowStepExecutionService` | imports `service.BusinessHoursService` (L10); builds `RunContext.now` at L241–242 | same SPI (a `now` contributor) |
| 3 | `WorkflowGraphValidator` | imports `service.person.PersonUpsertService` (L3); `validatePersonFieldReferences` L306–333 + person regex L29–36, called L117 | `GraphValidationRule` SPI |
| 4 | `AutomationWorkflowService` | imports `trigger.DomainEventTriggerValidator` (L7, field L27); `validate()` L281–286 **requires** a trigger + delegates | `TriggerValidator` SPI + make trigger presence host-policy |

> Leak #4 is **new** since the 2026-06-04 audit's "3 leaks" — found in the 2026-06-16 re-verification. It also exposes a hidden FUB assumption: `AutomationWorkflowService.validate()` hard-errors `"trigger is required"`. A domain-agnostic engine must let the host decide whether a trigger is mandatory.

### 2 schema FK leaks — engine tables FK'd to business tables

| # | Constraint | Migration | Fix |
|---|------------|-----------|-----|
| S1 | `fk_workflow_runs_webhook_event`: `workflow_runs.webhook_event_id → webhook_events(id)` | V10 L52–55 | drop FK; keep opaque `BIGINT` (rename `trigger_ref_id`) |
| S2 | `fk_workflow_runs_domain_event`: `workflow_runs.domain_event_id → events(id)` | V23 L9–13 | drop FK; keep opaque correlation id |

### Layering / packaging / naming
- **L1** — `WorkflowRunQueryService` imports `controller.dto.{WorkflowRunDetailResponse, WorkflowRunStepDetail, WorkflowRunSummary}`. Move the 3 DTOs **into** the engine.
- **L2** — `DomainEventScopeBuilder` sits in engine `expression/` but is host glue. Move out with the trigger code.
- **Naming** — `sourcePersonId` / `person` / `webhookEventId` / `domainEventId` are loose scalars with no FK, baked into the **public step SPI** (`StepExecutionContext`, `RunContext`, `WorkflowPlanRequest`, `ExpressionScope` keys, `WorkflowRunEntity` columns). Rename to `subjectId` / `subject` / `triggerEventId` **before v0.1** or it becomes a post-publish semver break.

---

## Phase 0 — Scratch spike: prove the cut compiles (throwaway, no commit)

**Goal:** empirically confirm that only the 4 code seams block a clean kernel compile — nothing hidden.

```bash
cd /Users/sarathkumar/Projects/2Creative/automation-engine

# 1. Census: every business import reachable from the kernel set. Expected hits = ONLY the 4 leaks.
grep -rnE "import com\.fuba\.automation_engine\.service\.(person|event|fub|webhook|auth|admin|note|call|BusinessHoursService)|import com\.fuba\.automation_engine\.(rules|client\.fub|exception\.(fub|person|webhook))|import com\.fuba\.automation_engine\.service\.workflow\.trigger" \
  src/main/java/com/fuba/automation_engine/service/workflow \
  src/main/java/com/fuba/automation_engine/persistence/entity/Workflow*.java \
  src/main/java/com/fuba/automation_engine/persistence/entity/Automation*.java
```

**Expected (and only) hits:** `WorkflowStepExecutionService` (×2), `WorkflowGraphValidator` (×1), `AutomationWorkflowService` (×1, the `trigger.` import). `WorkflowRunQueryService` shows the `controller.dto` import separately. The whole `trigger/` package + `DomainEventScopeBuilder` are host-glue and excluded from the kernel set. **If anything else appears, stop and re-audit — the boundary moved.**

No code change, no commit. This is a read-only gate.

---

## Phase 1 — Install the SPIs behind the seam (in this repo, single module, tests green)

**Goal:** remove all 4 code leaks behind kernel-owned SPIs + host adapters, **without moving any files**. Repo stays one module; everything stays `com.fuba.*`. This is the only phase with runtime-behaviour risk, so it is isolated and independently committable.

```bash
git checkout -b feature/engine-seams
```

New SPI package: `src/main/java/com/fuba/automation_engine/service/workflow/spi/`.

### 1a. `RunContextContributor` (leaks #1 + #2)

```java
// spi/RunContextContributor.java
package com.fuba.automation_engine.service.workflow.spi;

import java.util.Map;

/**
 * Host-pluggable contributor that fills ONE named block of the per-step run context.
 * Invoked exactly once per buildRunContext (i.e. per step) — preserves eager freshness:
 * a subject snapshot picks up a mid-run change; time-derived values re-cross boundaries.
 * Implementations MUST NOT cache and MUST NOT return null (return Map.of()).
 */
public interface RunContextContributor {
    /** Top-level run-context key this contributor fills, e.g. "person" or "now". */
    String key();
    Map<String, Object> contribute(RunContextRequest request);
}
```

```java
// spi/RunContextRequest.java — neutral names only; no FUB vocabulary leaks into the contract (G11)
package com.fuba.automation_engine.service.workflow.spi;

import java.time.Instant;

public record RunContextRequest(
        long runId,
        String workflowKey,
        String subjectId,
        String triggerEventId,
        Instant now) {}
```

> No no-op default needed: Spring injects an **empty `List<RunContextContributor>`** when no beans exist — exactly "the engine ships zero person/now knowledge."

### 1b. `GraphValidationRule` (leak #3)

```java
// spi/GraphValidationRule.java
package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import java.util.Map;

/** Host-pluggable per-node validation. Append human-readable errors to the list. */
public interface GraphValidationRule {
    void validateNode(Map<String, Object> node, String nodeId, List<String> errors);
}
```

### 1c. `TriggerValidator` (leak #4)

```java
// spi/TriggerValidator.java
package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import java.util.Map;

/** Host-pluggable save-time trigger-config validation. Default accepts anything. */
public interface TriggerValidator {
    /** Empty = valid. Never null. */
    List<String> validate(Map<String, Object> trigger);
}
```

```java
// spi/CamshaftSpiDefaults.java — boots without a host
package com.fuba.automation_engine.service.workflow.spi;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamshaftSpiDefaults {
    @Bean @ConditionalOnMissingBean(TriggerValidator.class)
    public TriggerValidator noopTriggerValidator() { return t -> List.of(); }
}
```

### 1d. Rewire `WorkflowStepExecutionService` (#1, #2)

Delete imports L10–11. Drop the two business constructor params (L57–58, fields L47–48), add `List<RunContextContributor>`. Replace the person/now block (L227–250) with a contributor loop — **keep `RunContext`'s typed `person`/`now` slots in this phase** (the rename is Phase 2):

```java
java.time.Instant nowInstant = clock.instant();
var req = new RunContextRequest(run.getId(), run.getWorkflowKey(),
        run.getSourcePersonId(),
        run.getWebhookEventId() != null ? String.valueOf(run.getWebhookEventId()) : null,
        nowInstant);

Map<String, Map<String, Object>> contributed = new java.util.HashMap<>();
for (var c : runContextContributors) {
    Map<String, Object> block = c.contribute(req);
    contributed.put(c.key(), block != null ? block : Map.of());
}
Map<String, Object> person = contributed.getOrDefault("person", Map.of());
Map<String, Object> now    = contributed.getOrDefault("now", Map.of());

return new RunContext(metadata,
        run.getTriggerPayload() != null ? run.getTriggerPayload() : Map.of(),
        run.getSourcePersonId(), person, now, stepOutputs);
```

Host adapters (stay in `com.fuba.*`, new pkg `service/workflow/contributor/`):

```java
@Component
public class PersonRunContextContributor implements RunContextContributor {
    private final PersonSnapshotResolver resolver;
    public PersonRunContextContributor(PersonSnapshotResolver r) { this.resolver = r; }
    public String key() { return "person"; }
    public Map<String, Object> contribute(RunContextRequest r) {
        Map<String, Object> m = resolver.resolve(r.subjectId());
        return m != null ? m : Map.of();
    }
}

@Component
public class NowRunContextContributor implements RunContextContributor {
    private final BusinessHoursService bh;
    public NowRunContextContributor(BusinessHoursService bh) { this.bh = bh; }
    public String key() { return "now"; }
    public Map<String, Object> contribute(RunContextRequest r) {
        return Map.of("isDaytime", bh.isDaytime(r.now()), "hourLocal", bh.hourLocal(r.now()));
    }
}
```

### 1e. Rewire `WorkflowGraphValidator` (#3)

Delete import L3. Add `List<GraphValidationRule>` ctor param. Replace L117 with a loop, and **delete** `validatePersonFieldReferences` + `collectPersonReferences` + `scanString` + the two `PERSON_*` patterns (L29–36):

```java
for (var rule : nodeRules) rule.validateNode(node, id, errors);   // was: validatePersonFieldReferences(node, id, errors);
```

Move the deleted logic + the `PersonUpsertService.capturedFieldNames()` call into a host `@Component` `PersonFieldGraphValidationRule implements GraphValidationRule` (new pkg `service/workflow/rule/`).

### 1f. Rewire `AutomationWorkflowService` (#4)

Delete import L7. Change the field/ctor param `DomainEventTriggerValidator → TriggerValidator`. Call `triggerValidator.validate(trigger)` at L284. Make `DomainEventTriggerValidator implements TriggerValidator` (adapt its method to return `List<String>`) so it auto-registers and Spring injects it in place of the no-op. **Also** soften the L281 `"trigger is required"` rule so trigger-presence is a host concern (e.g. gate it behind a property, default-on for FUB).

### Verify + commit

```bash
mvn -q -o compile
grep -rln "new WorkflowStepExecutionService(\|new WorkflowGraphValidator(\|new AutomationWorkflowService(" src/test/java
# update those test constructors: fake List<RunContextContributor> (a "now" contributor returning {isDaytime,hourLocal}),
# mock TriggerValidator, construct WorkflowGraphValidatorFieldReferenceTest with PersonFieldGraphValidationRule.
mvn -q -o test
git commit -am "refactor(workflow): extract RunContextContributor/GraphValidationRule/TriggerValidator SPIs"
```

**End state:** green build; the kernel has zero business imports except via SPIs. No files moved. Independently shippable even if you stop here.

---

## Phase 2 — New module, package moves, DTO move, scope-builder out, `sourcePersonId → subjectId`, FK drops

**Goal:** create `camshaft-engine`, move the 27 KERNEL + 7 KERNEL_LEAK files under `io.github.<handle>.camshaft.*`, move the 3 run DTOs in (L1), move `DomainEventScopeBuilder` + `trigger/` out (L2), rename `sourcePersonId → subjectId` across the public SPI, consolidate the migrations into one FK-free baseline, and ship the Spring Boot auto-config starter.

```bash
git checkout -b feature/engine-module
```

### 2a. Reactor + engine POM (import the BOM, don't inherit the parent — G8)

Root `pom.xml` → `<packaging>pom</packaging>` with `<modules>`: `camshaft-engine`, `app` (existing host moved under `app/`), `camshaft-examples`. Engine POM:

```xml
<project>
  <groupId>io.github.<handle></groupId>
  <artifactId>camshaft-engine</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <properties><java.version>21</java.version><spring-boot.version>4.0.3</spring-boot.version></properties>
  <dependencyManagement><dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version><type>pom</type><scope>import</scope></dependency>
  </dependencies></dependencyManagement>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency> <!-- DTOs only; v0.2 -core drops this -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jackson</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>com.dashjoin</groupId><artifactId>jsonata</artifactId><version>0.9.8</version></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  </dependencies>
  <!-- carry the byte-buddy-agent surefire argLine from the old pom (Mockito inline on JDK21 — G7) -->
</project>
```

Host `app` POM adds `spring-boot-starter-security`, `jjwt-*`, `devtools`, and a dependency on `camshaft-engine`.

### 2b. Move kernel files (`git mv` preserves history)

Move per the [ledger](./file-ledger.md) mapping into `camshaft-engine/src/main/java/io/github/<handle>/camshaft/`:
`core/orchestration`, `core/worker`, `core/control`, `core/graph`, `core/expression`, `core/http`, `core/query`, `core/execution`, `spi`, `api`, `persistence/entity`, `persistence/repository`, `config`, `support`. Rewrite `package`/`import` **per subpackage, recompiling after each** — a single mega-`sed` mis-targets because the package geometry differs per group; the compiler is the safety net (2g).

### 2c. Move the 3 run DTOs in (L1) + `WorkflowRunQueryService`

```bash
mkdir -p camshaft-engine/src/main/java/io/github/<handle>/camshaft/core/query/dto
git mv app/.../controller/dto/WorkflowRunDetailResponse.java  camshaft-engine/.../core/query/dto/
git mv app/.../controller/dto/WorkflowRunStepDetail.java       camshaft-engine/.../core/query/dto/
git mv app/.../controller/dto/WorkflowRunSummary.java          camshaft-engine/.../core/query/dto/
git mv app/.../service/workflow/WorkflowRunQueryService.java   camshaft-engine/.../core/query/
```
Update the host `AdminWorkflowRunController` import to the new engine DTO package (it serializes the views directly — shape is already API-shaped, no mapper needed).

### 2d. Move HOST_GLUE out (L2)

`DomainEventScopeBuilder` is the only file physically leaving the (now-deleted) engine `expression/` pkg:
```bash
git mv app/.../service/workflow/expression/DomainEventScopeBuilder.java \
       app/.../service/workflow/trigger/DomainEventScopeBuilder.java
```
The rest of `trigger/` + `config/WorkflowTriggerRouter*` already sit in host-named packages — leave them in `app`, just repoint their imports to the new `io.github.<handle>.camshaft.*` kernel coordinates (`WorkflowExecutionManager`, `WorkflowPlanRequest`, `WorkflowPlanningResult`, `AutomationWorkflowEntity`). Delete dead `TriggerTypeCatalogEntry` + the false `DomainEventScopeBuilder` javadoc (G13).

### 2e. `sourcePersonId → subjectId` rename (must land in v0.1 — G6)

- `RunContext`: `sourcePersonId → subjectId`, `person → subject`, `RunMetadata.webhookEventId → triggerEventId`; fix `{@link PersonSnapshotResolver}`/`BusinessHoursService` javadoc to plain prose (G14).
- `StepExecutionContext`: `sourcePersonId → subjectId` (both constructors).
- `WorkflowPlanRequest`: generalize `webhookEventId`/`domainEventId`/`sourcePersonId → subjectId` + `triggerEventId` (keep a back-compat ctor defaulting nulls).
- `WorkflowRunEntity`: `@Column(name="source_entity_id")` for `subjectId`; `trigger_ref_id` / `domain_event_id` opaque, no FK.
- `WorkflowRunRepository`: rename derived finders (`…BySubjectId…`).
- `RunSupersedePolicy`: param + javadoc.
- `ExpressionScope`: **dual-emit** both `subjectId` (new) and `sourcePersonId` (legacy alias) for one release so deployed `{{ sourcePersonId }}` JSON keeps resolving. The host's 8 steps in `app` switch to `context.subjectId()`.

### 2f. One consolidated FK-free baseline migration (G5, S1, S2)

The engine ships **one hand-authored** `camshaft-engine/src/main/resources/db/migration/camshaft/V1__camshaft_schema.sql`, written to the **final** shape — **not** a concat of V10/V11/V16/V23 (those are ALTERs against V10, and the `source_lead_id → source_person_id` rename lives in host V21). Fold in: `version_number INT NOT NULL DEFAULT 1` + its unique index (V11), the 4-value status CHECK incl. `ARCHIVED` (V11), `step_state JSONB` (V16); columns renamed to `source_entity_id` / `trigger_ref_id` / `domain_event_id` with **no business FKs**; keep only the internal FKs (`workflow_runs → automation_workflows`, `workflow_run_steps → workflow_runs`). Bind the engine to its own Flyway location so it never collides with the host's V1–V23.

Integrated host that wants RI re-adds the FKs in a **host overlay** migration (ordered after `events`/`webhook_events` exist):
```sql
-- app/.../db/migration/Vxx__reattach_engine_fks.sql  (G4)
ALTER TABLE workflow_runs ADD CONSTRAINT fk_workflow_runs_webhook_event
    FOREIGN KEY (trigger_ref_id) REFERENCES webhook_events (id) ON DELETE SET NULL;
ALTER TABLE workflow_runs ADD CONSTRAINT fk_workflow_runs_domain_event
    FOREIGN KEY (domain_event_id) REFERENCES events (id) ON DELETE SET NULL;
```
Move `WorkflowRunsV23MigrationPostgresRegressionTest` to `app` and re-point it at the **host overlay** constraint (it can't live in the engine — no `events` table there).

### 2g. Bean-conflict hardening + auto-config starter (G1, G2)

- **Drop `JacksonConfig`** from the engine (G1) — the kernel never uses `ObjectMapper`; the Slack example builds its own. Apply `@ConditionalOnMissingBean` to `TimeConfig`'s `Clock`, `HttpClientConfig`'s `RestClient.Builder`, and the HTTP adapter.
- Ship the starter:

```java
// io/github/<handle>/camshaft/CamshaftAutoConfiguration.java
@AutoConfiguration
@ComponentScan("io.github.<handle>.camshaft")
@EntityScan("io.github.<handle>.camshaft.persistence.entity")
@EnableJpaRepositories("io.github.<handle>.camshaft.persistence.repository")
@EnableScheduling
@EnableConfigurationProperties({WorkflowWorkerProperties.class, WorkflowStepHttpProperties.class})
public class CamshaftAutoConfiguration {}
```
Register it in `camshaft-engine/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.github.<handle>.camshaft.CamshaftAutoConfiguration
```
`@EntityScan`/`@EnableJpaRepositories` are **mandatory** now that entities/repos left the host root package (G2).

### Verify + commit

```bash
mvn -q -o -pl camshaft-engine compile   # engine compiles standalone
mvn -q -o install                      # full reactor (engine + app) wires
mvn -q -o -pl app test                 # host tests green (overlay FK regression re-pointed)
# run the clean-kernel grep assertions (below)
```

---

## Phase 3 — Examples, sample workflow, README, docker-compose, standalone IT

**Goal:** make the engine usable and **prove** it runs with no FUB infrastructure present.

```bash
git checkout -b feature/engine-examples
```

### 3a. `camshaft-examples` module + the 5 example steps
`git mv` `Delay/SetVariable/BranchOnField/HttpRequest/SlackNotify` steps into `camshaft-examples/.../examples/steps`, repackage, repoint imports to `camshaft.spi`/`.core.expression`/`.core.http`. The 8 HOST steps (`Fub*`, `AiCall*`, `WaitAndCheck*`) **stay in `app`** and register against the kernel registry. Move `WorkflowRestHttpClientAdapter` into engine `core/http` as the default `@ConditionalOnMissingBean WorkflowHttpClient` so `http_request`/`slack_notify` have a bean (G10).

### 3b. `sample-workflow.json`
3-node graph `set_variable → delay(2m) → http_request` (+ a `branch_on_field`), plain trigger, `{{ subjectId }}` / `{{ now.isDaytime }}` — no FUB vocabulary.

### 3c. `docker-compose.yml` — `postgres:16` (db/user/pass = `camshaft`).

### 3d. Standalone integration test (the success-criterion proof — G3)
`CamshaftSmokeIT` with its **own** tiny `@SpringBootConfiguration` (NOT `AutomationEngineApplication`) + Testcontainers Postgres: registers `Delay` + `SetVariable`, defines a delayed JSON workflow, calls `WorkflowExecutionManager.plan()`, advances the injected `Clock`, polls the worker, asserts the run COMPLETES — then boots a fresh context against the same DB to prove **delay survives restart**. This is ~1 day of new authoring; the old `WorkflowEngineSmokeTest`/`BugHuntTest` stay in `app` (they need the full host context — G3).

### 3e. README + walkthrough + LICENSE
`README.md`: Apache-2.0, min Boot version, "implement `WorkflowStepType` in ~30 lines + register the bean" walkthrough, docker-compose instructions, note Docker is required for the test suite (G7). `LICENSE` = Apache-2.0. Engine `application.properties` example carries only `workflow.worker.*` + `workflow.step-http.*` + datasource/jpa/flyway (G15).

### Verify
```bash
mvn -q -o install
mvn -q -o -pl camshaft-engine verify         # CamshaftSmokeIT against Testcontainers
docker compose -f camshaft-examples/docker-compose.yml up -d
```

---

## "Kernel is clean" — success assertions (run after Phase 2; all must pass)

```bash
# 1. No business imports anywhere in the engine module
! grep -rE "import com\.fuba\.|import .*\.(person|event|fub|webhook|auth|admin|rules)\." camshaft-engine/src/main/java
# 2. No reach into host controller.dto (L1 gone)
! grep -rn "controller\.dto" camshaft-engine/src/main/java
# 3. No FUB vocabulary in the public SPI/API/state surface (subjectId/subject/triggerEventId only; ExpressionScope legacy alias is the documented exception)
! grep -rnw "sourcePersonId\|webhookEventId\|domainEventId" camshaft-engine/src/main/java/io/github/<handle>/camshaft/spi camshaft-engine/src/main/java/io/github/<handle>/camshaft/api
# 4. No business FK in the engine baseline migration
! grep -niE "REFERENCES (webhook_events|events|persons|leads)" camshaft-engine/src/main/resources/db/migration/camshaft/V1__camshaft_schema.sql
# 5. Engine self-registers
test -f camshaft-engine/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# 6. Engine compiles with no dependency on the app module
mvn -q -o -pl camshaft-engine compile
```
Behavioural criterion (Phase 3): `mvn -q -o -pl camshaft-engine verify` green with no FUB-shaped bean in the context.

---

## Risk register

| # | Risk | Phase | Sev | Mitigation |
|---|------|-------|-----|------------|
| R1 | `JacksonConfig` bare `ObjectMapper` hijacks host mapper, breaks JSR-310 | 2 | High | Drop it from the engine (kernel doesn't use it) — G1 |
| R2 | Engine beans never register in an arbitrary host | 2 | High | Ship `CamshaftAutoConfiguration` + `AutoConfiguration.imports` + `@EntityScan`/`@EnableJpaRepositories` — G2 |
| R3 | V23 schema-regression test asserts the FK being dropped → red build | 2 | High | Move to `app`, re-point at host overlay FK — G4 |
| R4 | Consolidated `V1` mis-authored (missing `version_number`/`ARCHIVED`/`step_state`, or stray FK) | 2 | High | Author from final live shape, not concat; grep assertion #4 + CamshaftSmokeIT — G5 |
| R5 | `sourcePersonId→subjectId` breaks deployed `{{ sourcePersonId }}` JSON | 2 | Med | Dual-emit both scope keys one release; deprecate next — G6 |
| R6 | Test constructors break on new SPI signatures | 1 | Med | Grep `new WorkflowStepExecutionService(`… first; swap mocks to SPI types |
| R7 | `http_request`/`slack_notify` throw `NoSuchBeanDefinitionException` (no HTTP client) | 3 | Med | Move `WorkflowRestHttpClientAdapter` into engine `core/http` as `@ConditionalOnMissingBean` — G10 |
| R8 | H2 can't exercise `SKIP LOCKED`/Flyway | 3 | Med | Testcontainers is a hard dep; carry `byte-buddy-agent` argLine; gate claim/worker/migration tests behind Postgres — G7 |
| R9 | `RunContextContributor` impl caches → breaks per-step freshness | 1 | Low | Javadoc the once-per-step contract; unit-test per-build invocation — G11 |
| R10 | `webmvc` in "pure" core (3 DTOs) contradicts v0.2 `-core/-spring` split | 2 | Low | Accept for v0.1; flag for the v0.2 split — G8 |
| R11 | `AiCallWorkflowStep` latent `TO_MISSING` bug travels under new package | 3 | Low | Stays HOST; note so host doesn't inherit silently — G12 |

---

## Bottom line / effort

- **Phase 0** — read-only gate, proves the cut. Minutes.
- **Phase 1** — 3 SPIs (`RunContextContributor`, `GraphValidationRule`, `TriggerValidator`) + host adapters, in-place, green, committable. **No file moves.** ~1 day incl. test fixes.
- **Phase 2** — module + moves + DTO move + scope-builder out + `sourcePersonId→subjectId` + consolidated FK-free baseline + auto-config starter. ~1.5–2 days (the migration baseline + bean wiring are the real work, not the moves).
- **Phase 3** — examples module + sample JSON + docker-compose + standalone IT + README. ~1.5–2 days (net-new authoring, **not** packaging).

Realistic total: **~4–5 focused days**, not the half-day the "clean lift" framing implies — the import boundary is clean, but the Spring wiring, the Flyway baseline, and the example/test deliverables are genuine work. Each phase ends green and is independently committable, so you can land Phase 1 today and stage the rest.
