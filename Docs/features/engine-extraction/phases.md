# Engine Extraction — Pre-Lift Checklist & Phases

> **Status:** `NOT STARTED` — boundary verified (see [`README.md`](./README.md), 2026-06-04); unblocked (domain-events Phases 0–5 merged).
>
> **Strategy:** Decouple **in-place in this repo first** (introduce the SPIs, let the host wire through them, keep the full test suite green), *then* copy the already-clean engine into the new repo. The existing suite proves the decoupling before any code physically moves.
>
> **Verified dependency baseline:** Spring Boot `4.0.3`, Java `21`, JSONata `com.dashjoin:jsonata:0.9.8`.
>
> **Critical path:** Group 0 → Group 2 → Group 3 → **the lift**. Groups 1, 4, 5, 6, 7, 8 run in parallel with Group 2.
> **Effort before the lift:** ~4–5 focused days, dominated by Groups 2 and 8.
>
> **Hard constraint — Postgres-only:** the engine uses Postgres-specific features (JSONB columns, and `FOR UPDATE OF steps SKIP LOCKED` in `JdbcWorkflowRunStepClaimRepository` for the step-claim queue). The OSS engine is Postgres-only; tests require Testcontainers-Postgres (no H2 substitute). State this explicitly in the README.

---

## Group 0 — Finalize decisions (blocks everything; ~1 sitting)
Status: `NOT STARTED`

- [ ] **0.1** Lock GitHub handle/org for namespace → `io.github.<handle>.inline.*` (or buy a domain for `io.inline` / `dev.inline`).
- [ ] **0.2** Module shape for v0.1: single `inline-engine` artifact (recommended) vs. early `-core`/`-spring` split (deferred to v0.2).
- [ ] **0.3** `domain_event_id` column fate in OSS schema: keep as opaque correlation `BIGINT`, or drop entirely. *Recommend: rename `webhook_event_id`→generic, drop `domain_event_id`.*
- [ ] **0.4** Trigger-validation seam: fold host trigger validation into the `GraphValidationRule` SPI, or a dedicated `TriggerValidator` hook. (Affects how `DomainEventTriggerValidator` re-plugs.)
- [ ] **0.5** Repo name + visibility (public from day 1 vs. private until polished).

→ When settled, record in [`RD-007`](../../repo-decisions/RD-007-engine-as-standalone-library.md).

## Group 1 — Dependency & module-shape inventory (~half day)
Status: `NOT STARTED`

- [ ] **1.1** Confirm engine **runtime** deps; drop the rest.
  - Keep: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `spring-boot-starter-jackson`, `com.dashjoin:jsonata:0.9.8`, `postgresql`, `spring-boot-starter-validation` (verify usage).
  - Drop: `spring-boot-starter-security`, `jjwt-*`, `devtools`, and `spring-boot-starter-webmvc` (unless shipping admin controllers — see 1.3).
- [ ] **1.2** Verify Lombok usage in the kernel; if clean, drop Lombok from the engine pom.
- [ ] **1.3** Decide whether the engine ships the 2 admin controllers (`AdminWorkflowController`, `AdminWorkflowRunController`) + DTOs. Yes → keep `webmvc`. No → pure library, controllers live in the example app only.
- [ ] **1.4** Pin Spring Boot 4.0.3 / Java 21 as the engine baseline.

## Group 2 — In-place decoupling refactors (in THIS repo, tests green; ~1–1.5 days)
Status: `NOT STARTED`

> Each task: change → run full suite → commit. No code moves yet.

- [ ] **2.1** Introduce `RunContextContributor` SPI; refactor `WorkflowStepExecutionService.buildRunContext` to take `List<RunContextContributor>`. **Fixes code leaks #1 + #2.**
- [ ] **2.2** Move `PersonSnapshotResolver` + `BusinessHoursService` into two host-side contributor beans (`person`, `now`). Engine stops importing both.
- [ ] **2.3** Introduce `GraphValidationRule` SPI; move `WorkflowGraphValidator.validatePersonFieldReferences()` into a host-registered rule. **Fixes code leak #3.**
- [ ] **2.4** Re-plug `DomainEventTriggerValidator` through the chosen seam (per 0.4) so the engine save path no longer hard-calls it.
- [ ] **2.5** **L1:** move `WorkflowRunDetailResponse`, `WorkflowRunStepDetail`, `WorkflowRunSummary` out of `controller.dto` into the workflow package; fix `WorkflowRunQueryService`.
- [ ] **2.6** **L2:** move `DomainEventScopeBuilder` out of `service/workflow/expression/` into the host trigger package.
- [ ] **2.7** **Naming:** rename `sourcePersonId` → `subjectId` (or `sourceEntityId`) across `RunContext`, `StepExecutionContext`, `WorkflowRunEntity`, `RunSupersedePolicy`, **and the public entry DTO `WorkflowPlanRequest`** (which also carries business-flavored `webhookEventId` / `domainEventId` / `source` — generalize to opaque correlation ids). (DB column rename → 5.2.)
- [ ] **2.8** Confirm zero `service.person.*` / `service.event.*` / `service.BusinessHoursService` imports remain under `service/workflow/`.

## Group 3 — De-risk spike (~half day, after Group 2)
Status: `NOT STARTED`

- [ ] **3.1** Scratch Maven module: copy clean `service/workflow/` + 3 entities + repos + engine config + migrations V10/V11/V16.
- [ ] **3.2** Stub the 2 contributor SPIs with no-ops; drop the 2 FK constraints from the copied migrations.
- [ ] **3.3** Confirm it compiles and the Spring context starts standalone against throwaway Postgres. Discard the module after — this is the "really this clean?" proof.

## Group 4 — New OSS project scaffolding (~half day, parallel with Group 2)
Status: `NOT STARTED`

- [ ] **4.1** Create GitHub repo (`inline` / `inline-engine`); branch protection.
- [ ] **4.2** Maven skeleton: `pom.xml` (group `io.github.<handle>`, artifact `inline-engine`, Boot 4.0.3, Java 21, deps from Group 1), `.gitignore`, `.mvn` wrapper.
- [ ] **4.3** `LICENSE` (Apache 2.0) + per-file header policy.
- [ ] **4.4** Layout: `inline-engine/` (library) + `examples/` (runnable Boot app) + `docs/`.
- [ ] **4.5** CI: GitHub Actions build + test (Postgres service container / Testcontainers).
- [ ] **4.6** `docker-compose.yml` (Postgres) for the example app + local dev.
- [ ] **4.7** **Spring Boot auto-configuration** (the task that makes it a real library). Today there is **no auto-config** — the engine only works via the host's component scan. Add: an `@AutoConfiguration` class registering the engine beans, `@EntityScan`/`@EnableJpaRepositories` for the engine's entities + repos, a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` entry, and default `@ConditionalOnMissingBean` beans for `Clock` and `ObjectMapper`. Goal: a consumer adds the jar + a datasource and the engine just works.
- [ ] **4.8** Example app: a runnable trigger entrypoint (`POST /trigger` controller calling `plan()`) + `application.yml` with engine defaults (`workflow.worker.*`, datasource).
- [ ] **4.9** OSS repo hygiene: `CONTRIBUTING.md`, `SECURITY.md`, issue/PR templates, README badges.
- [ ] **4.10** (Defer) Maven Central / OSSRH publishing + GPG signing — only at `0.1.0` release time.

## Group 5 — Schema / migration prep (~2 hours)
Status: `NOT STARTED`

- [ ] **5.1** Consolidate a clean engine migration `V1__create_workflow_engine_tables.sql` from V10 + V11 + V16, **without** the `webhook_events`/`events` FK constraints (S1, S2).
- [ ] **5.2** Apply the column rename (`source_person_id` → `source_entity_id`) in that migration **and in the native SQL** of `JdbcWorkflowRunStepClaimRepository` (raw SQL — not covered by the JPA rename in 2.7).
- [ ] **5.3** Encode the `domain_event_id` outcome (per 0.3). Also pick a sensible default/nullable for the `source` `NOT NULL` column (today expects a system label like "FUB").
- [ ] **5.4** **Flyway coexistence:** decide how engine migrations live alongside a consumer's own Flyway setup — dedicated schema + history table, or a namespaced migration location — so adopting the engine doesn't collide with the host's `V1__...`.

## Group 6 — Showcase / docs prep (lightweight; parallel)
Status: `NOT STARTED`

- [ ] **6.1** Write the showcase brief (tagline, persona, 3 README proof points, non-goals).
- [ ] **6.2** Draft the "build your own step in 30 lines" walkthrough (`HttpRequestWorkflowStep` is the cleanest template).
- [ ] **6.3** Confirm the 5 example steps to ship: `http_request`, `delay`, `set_variable`, `branch_on_field`, `slack_notify`.
- [ ] **6.4** Author 1–2 sample workflow JSONs (JSONata across steps + a resumable delay).

## Group 7 — Open-source readiness: legal & safety (~half day; BEFORE repo goes public)
Status: `NOT STARTED`

- [ ] **7.1** **Secret hygiene (blocking for public visibility).** The repo has live `.env` and `.env.prod` plus FUB API keys in config. Decide **fresh repo vs. filtered history** for the OSS project; if any history is carried over, run a secret scanner (gitleaks/trufflehog) over it. Ensure every example/config file in the OSS repo contains only placeholders.
- [ ] **7.2** **Dependency license audit.** Verify `com.dashjoin:jsonata:0.9.8` is redistributable under Apache 2.0 (it underpins the whole expression layer — an incompatible license forces a JSONata alternative). Spot-check all other transitive deps for copyleft surprises.
- [ ] **7.3** `NOTICE` file (Apache convention) + attribution for bundled/third-party code.
- [ ] **7.4** Confirm `.gitignore` covers `.env*` and any local secret files in the new repo.

## Group 8 — Test suite port (~1 day; runs with the lift)
Status: `NOT STARTED`

- [ ] **8.1** Triage the **29 test files under the workflow package** (of 115 total): which are pure-engine (port as-is) vs. which depend on FUB-shaped fixtures (need generic fixtures).
- [ ] **8.2** Port the pure-engine tests (graph validator, execution, retry/supersede, expression evaluator) into the new module.
- [ ] **8.3** Re-base the replay harness and any concurrency/stress tests onto **generic fixtures** (no FUB person webhooks) so they ship as OSS examples of how to test workflows.
- [ ] **8.4** Wire Testcontainers-Postgres into the new module's test profile (required — no H2).
- [ ] **8.5** Target: green suite in CI proving the engine works standalone — this is also the credibility showcase.

---

## Then: the lift itself

Once Groups 0–6 are done and Group 3 has proven a standalone compile, the lift is mostly mechanical. Future phase docs (write when starting):

```
phase-1-contributor-spi.md   ← detailed SPI design (if 2.1/2.3 need more than the in-place refactor)
phase-2-module-split.md      ← physically move clean code into the Group 4 scaffold;
                                wholesale package rename com.fuba.automation_engine.* → io.github.<handle>.inline.*
                                (large mechanical import rewrite across every moved file)
phase-3-examples-readme.md   ← example steps, sample workflow JSON, README, docker-compose
```

The bulk of post-prep work is packaging, not refactoring — see [`README.md`](./README.md) "Effort".
