# Engine Extraction — File Ledger (needed vs not-needed)

> **Re-verified against current code 2026-06-16** (branch `feature/multi-event-triggers`, HEAD `5f4c3d2`).
> Method: exhaustive per-file classification of all 235 main + 115 test Java files + 23 migrations + resources + `pom.xml`, plus adversarial re-verification of every coupling point. The two most recent commits (`89cfed6` person-profile-enrichment, `5f4c3d2` MVP workflow) are **docs-only** — no code changed since the 2026-06-04 boundary audit, so that audit still holds, **with one addition**: a 4th code leak was found (see below).

This ledger answers one question per file: **does it ship in the standalone engine, or stay in the host?** It is the input to [`migration-runbook.md`](./migration-runbook.md).

Legend:
- **KERNEL** — ships in the engine module as-is (only the `package` line changes).
- **KERNEL_LEAK** — belongs in the engine but currently reaches into business code; ships **after** a seam is added in Phase 1.
- **EXAMPLE** — generic step/adapter; ships as an OSS *example*, not part of the core.
- **HOST_GLUE** — physically inside the engine packages today but is host code; moves **out**.
- **HOST** — stays in the FUB app, never moves.

---

## Summary counts

| Bucket | Main `.java` | Destination |
|---|---:|---|
| KERNEL (move as-is) | 27 | engine `core` / `spi` / `api` / `persistence` / `config` / `support` |
| KERNEL_LEAK (move after seam) | 7 + 3 DTOs | engine, after Phase 1 SPIs + DTO move |
| EXAMPLE (5 steps + 1 HTTP adapter) | 6 | `camshaft-examples` module |
| HOST_GLUE (move out) | 6 | stays in host (`com.fuba.*`), repackaged |
| HOST (never moves) | ~190 | stays in host |
| Engine migrations | 4 DDL fragments → **1** consolidated baseline | engine |

The "engine surface" is small: **34 Java files** (27 KERNEL + 7 KERNEL_LEAK) + 3 DTOs move into the engine. Everything else either stays put or ships as an example.

---

## INSIDE the engine

### KERNEL — 27 files, move verbatim (package line only)

**Orchestration / entry (`core/`)**
```
service/workflow/WorkflowExecutionManager.java     ← the public entry point: plan(WorkflowPlanRequest). Imports persistence + KeyNormalizationHelper only. CLEAN.
service/workflow/RunSupersedePolicy.java           ← run-collision policy; person-flavoured naming only, operates on entities + payload keys.
service/workflow/WorkflowExecutionDueWorker.java   ← scheduled poll of due steps; depends only on WorkflowWorkerProperties.
service/workflow/WorkflowRunControlService.java    ← cancel/supersede; persistence only.
```

**Step SPI (`spi/`)** — the extension point, all pure
```
service/workflow/WorkflowStepType.java
service/workflow/WorkflowStepRegistry.java          ← tolerates an empty List<WorkflowStepType> (verified): engine ships zero steps by default.
service/workflow/StepExecutionResult.java
service/workflow/RetryPolicy.java
```

**Planning result / graph result (`api/`, `core/graph/`)**
```
service/workflow/WorkflowPlanningResult.java
service/workflow/GraphValidationResult.java
```

**Expression layer (`core/expression/`)** — pure
```
service/workflow/expression/ExpressionEvaluator.java
service/workflow/expression/JsonataExpressionEvaluator.java
```

**Generic HTTP infra (`core/http/`)** — pure
```
service/workflow/http/WorkflowHttpClient.java
service/workflow/http/WorkflowHttpClientException.java
service/workflow/http/WorkflowHttpRequest.java
service/workflow/http/WorkflowHttpResponse.java
```

**Persistence — engine entities + repos** (zero ORM relationships to business entities — verified)
```
persistence/entity/AutomationWorkflowEntity.java
persistence/entity/WorkflowStatus.java
persistence/entity/WorkflowRunEntity.java
persistence/entity/WorkflowRunStatus.java
persistence/entity/WorkflowRunStepEntity.java
persistence/entity/WorkflowRunStepStatus.java
persistence/repository/AutomationWorkflowRepository.java
persistence/repository/WorkflowRunRepository.java
persistence/repository/WorkflowRunStepRepository.java
persistence/repository/WorkflowRunStepClaimRepository.java          ← claim/skip-locked queue interface
persistence/repository/JdbcWorkflowRunStepClaimRepository.java      ← Postgres FOR UPDATE … SKIP LOCKED (Postgres-only; see runbook G7)
```

**Engine config (`config/`)** — see G1 caveat for `JacksonConfig`
```
config/TimeConfig.java                       ← Clock bean (make @ConditionalOnMissingBean)
config/HttpClientConfig.java                 ← RestClient.Builder (make @ConditionalOnMissingBean)
config/WorkflowWorkerProperties.java
config/WorkflowWorkerSchedulingConfig.java   ← @EnableScheduling lives here
config/WorkflowStepHttpProperties.java
config/WorkflowStepHttpConfig.java
```

**Support (`support/`)**
```
service/support/KeyNormalizationHelper.java  ← pure; used by WorkflowExecutionManager + AutomationWorkflowService
```

> **`JacksonConfig.java` is NOT in the kernel set.** It ships a bare `@Bean ObjectMapper` (no JavaTimeModule) that would hijack any host's mapper, and the kernel never autowires `ObjectMapper` (only host-glue + the Slack example do). **Drop it from the engine.** See runbook **G1**.

> **`WorkflowRestHttpClientAdapter`** (`client/http/`) is the only `implements WorkflowHttpClient`. It is KERNEL-grade and must travel into the engine `core/http` as the default `@ConditionalOnMissingBean` client, or the `http_request`/`slack_notify` examples have no bean. See runbook **G10**.

### KERNEL_LEAK — 7 files + 3 DTOs, move after the Phase 1 seams

| File | Leak / issue | Fix (Phase) |
|---|---|---|
| `service/workflow/WorkflowStepExecutionService.java` | imports `service.person.PersonSnapshotResolver` (L11) + `service.BusinessHoursService` (L10); builds `person` (L233) + `now` (L241–242) in `buildRunContext` | `RunContextContributor` SPI (P1) |
| `service/workflow/WorkflowGraphValidator.java` | imports `service.person.PersonUpsertService` (L3); `validatePersonFieldReferences` (L306–333) + 2 person regex (L29–36) + `collectPersonReferences`/`scanString`, called L117 | `GraphValidationRule` SPI (P1) |
| `service/workflow/AutomationWorkflowService.java` | **4th leak** — imports `trigger.DomainEventTriggerValidator` (L7, field L27); `validate()` L281–286 hard-requires a trigger and delegates to it | `TriggerValidator` SPI + make trigger presence host-policy (P1) |
| `service/workflow/WorkflowRunQueryService.java` | imports `controller.dto.{…}` (L1 layering inversion) | move the 3 DTOs in (P2) |
| `service/workflow/RunContext.java` | naming: `sourcePersonId`, `person`, `RunMetadata.webhookEventId` baked into run-state shape | rename `→ subjectId/subject/triggerEventId` (P2) |
| `service/workflow/StepExecutionContext.java` | naming: `sourcePersonId` in the **public step SPI** every step sees | rename `→ subjectId` (P2, pre-v0.1) |
| `service/workflow/WorkflowPlanRequest.java` | shape: `webhookEventId`/`domainEventId`/`sourcePersonId` (all loose scalars) | generalize `→ subjectId`/`triggerEventId` (P2) |
| `service/workflow/expression/ExpressionScope.java` | naming: emits `sourcePersonId` + `person` scope keys | rename + dual-emit legacy alias one release (P2) |
| `controller/dto/WorkflowRunDetailResponse.java` | host-layer DTO the engine reads | **move into** engine `core/query/dto` (P2) |
| `controller/dto/WorkflowRunStepDetail.java` | same | move in (P2) |
| `controller/dto/WorkflowRunSummary.java` | same | move in (P2) |

All `sourcePersonId`/`webhookEventId`/`domainEventId`/`source_person_id` are **loose `String`/`Long`/`VARCHAR` with no FK** — pure rename, no behaviour change.

---

## OUTSIDE the engine

### EXAMPLE — 6 files, ship in the `camshaft-examples` module
```
service/workflow/steps/HttpRequestWorkflowStep.java
service/workflow/steps/DelayWorkflowStep.java
service/workflow/steps/SetVariableWorkflowStep.java
service/workflow/steps/BranchOnFieldWorkflowStep.java
service/workflow/steps/SlackNotifyWorkflowStep.java
client/http/WorkflowRestHttpClientAdapter.java   ← default WorkflowHttpClient (could also live in engine core/http; see G10)
```

### HOST_GLUE — 6 files, move OUT (stay in `com.fuba.*`, repackaged)
```
service/workflow/expression/DomainEventScopeBuilder.java   ← imports DomainEvent; used ONLY by DomainEventTriggerType. Move to host trigger pkg (L2).
service/workflow/trigger/WorkflowTriggerRouter.java        ← drives engine via plan(); imports service.event.*
service/workflow/trigger/DomainEventTriggerType.java       ← imports DomainEvent + PersonSnapshotResolver
service/workflow/trigger/DomainEventTriggerValidator.java  ← imports PersonDiffComputer + PersonUpsertService; becomes the host's TriggerValidator impl
service/workflow/trigger/EntityRef.java                    ← travels with the trigger code
config/WorkflowTriggerRouterProperties.java + WorkflowTriggerRouterConfig.java  ← host trigger-router config
```

### HOST — never moves (stay in the FUB app)

Summarised by package (all business / integration / ingestion):

| Package | Files | Why HOST |
|---|---:|---|
| `service/workflow/steps/Fub*`, `AiCall*`, `WaitAndCheck*` | 8 | business step impls; register against the kernel registry from the host |
| `service/workflow/aicall/*` | 4 | AI-call client SPI used by `AiCallWorkflowStep` |
| `service/person/*` | ~4 | `PersonSnapshotResolver`, `PersonUpsertService`, `PersonDiffComputer` — re-supplied to the engine via SPIs |
| `service/event/*` | several | `DomainEvent`, `EngineWriteCoordinator`, `EngineEchoGate` — host event store |
| `service/webhook/**` | many | webhook ingestion/dispatch/parse |
| `service/fub/*`, `client/fub/**` | many | Follow Up Boss integration |
| `service/note`, `service/call`, `service/auth`, `service/admin` | several | business services |
| `service/BusinessHoursService` | 1 | re-supplied via `RunContextContributor` |
| `rules/*`, `service/model/*` | ~14 | call-decision business rules |
| `controller/*` (incl. `AdminWorkflowController`, `AdminWorkflowRunController`) | 11 | host HTTP surface; call the engine's public API |
| `controller/dto/*` (minus the 3 run DTOs) | ~34 | host API DTOs |
| `persistence/entity` business + repos | ~18 | `PersonEntity`, `WebhookEventEntity`, `EventEntity`, `ProcessedCallEntity`, `AppUserEntity` + repos |
| `config/*` business | ~14 | `Fub*`, `BusinessHours*`, `CallOutcome*`, `AiCall*`, `Webhook*`, `Jwt*`, `AdminAuth*`, `SecurityConfig`, `security/*` |
| `exception/**` | several | business exceptions |
| `AutomationEngineApplication.java` | 1 | host bootstrap (`@SpringBootApplication` at root) |

### The 4 reverse-dependency files (still exactly 4 — verified)
Files OUTSIDE `service/workflow/` that import FROM it. After the lift, each consumes only the engine's **public** API:
```
controller/AdminWorkflowController.java        ← trigger source + CRUD; calls AutomationWorkflowService
controller/AdminWorkflowRunController.java      ← read controller; uses the 3 run DTOs (now engine-owned)
client/http/WorkflowRestHttpClientAdapter.java  ← implements engine WorkflowHttpClient (ships as example/engine default)
client/aicall/AiCallServiceHttpClientAdapter.java ← implements the host aicall SPI (HOST)
```

---

## Resources & build

| Item | Verdict | Note |
|---|---|---|
| `db/migration/V10__create_workflow_engine_tables.sql` | engine (consolidate) | creates the 3 engine tables; **drop FK `fk_workflow_runs_webhook_event`** (L52–55, S1) |
| `db/migration/V11__add_workflow_version_number.sql` | engine (fold in) | adds `version_number` + `ARCHIVED` status + unique index |
| `db/migration/V16__add_step_state_to_workflow_run_steps.sql` | engine (fold in) | adds `step_state` JSONB |
| `db/migration/V23__add_domain_event_id_to_workflow_runs.sql` | engine (fold in) | adds `domain_event_id`; **drop FK `fk_workflow_runs_domain_event`** (L9–13, S2) |
| `db/migration/V21__…rename…` | HOST | but contains `ALTER TABLE workflow_runs RENAME COLUMN source_lead_id → source_person_id` (L27) — an engine-column mutation living in a host migration; the consolidated baseline must use the **final** name |
| all other `V1–V22` migrations | HOST | business tables |
| `application.properties` / `application-prod.properties` | split | engine keeps `workflow.worker.*`, `workflow.step-http.*`, datasource/jpa/flyway; host keeps `workflow.trigger-router.*`, `engine.events.*`, jwt/security/fub (G15) |
| `logback-spring.xml` | HOST | example only in engine |
| `pom.xml` | split | engine needs jpa, validation, webmvc (DTOs only), jackson, flyway(+postgresql), jsonata 0.9.8, postgresql, lombok, testcontainers; host adds security, jjwt, devtools (G8) |
| `ui/**` | OUT | the React admin UI is not part of either deliverable |

→ The 4 engine DDL fragments are spread across V10/V11/V16/V23 **and** entangled with host V21. They **cannot be copied verbatim** — they consolidate into one hand-authored `V1__engine_schema.sql` at the engine's own Flyway location. See runbook **Phase 2f** + **G5**.
