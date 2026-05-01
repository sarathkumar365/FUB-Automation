# Dev Hosting Security Hardening — Phases

**Scope:** items A1, A3, A5, A6, A7 from
[`Docs/hosting-decision/dev/dev-hosting-security-checklist.md`](../../hosting-decision/dev/dev-hosting-security-checklist.md).
A2 and A4 are deferred and tracked as accepted known issues — see [research.md](./research.md).

**Plan source of truth:** [`plan.md`](./plan.md).

**Baseline:** 2026-05-01.

**Branch:** per `AGENTS.md`, this work belongs on a new
`feature/dev-hosting-security-hardening` cut from `dev`, with phase branches off it.
The current working branch (`feature/lead-management-platform`) is the wrong parent and
carries unrelated uncommitted changes; switching branches is a pre-implementation step
captured as Phase 0 below.

## Legend

- Status: `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` dropped (with reason)
- Severity: **H** high-impact · **M** medium · **L** polish
- Each phase has its own `phase-<n>-implementation.md` log once started.

## Per-phase mandatory closing gate

Every phase log MUST answer the **Repo decisions impact** question before being marked done:
- *"Does anything in this phase warrant promoting to `Docs/repo-decisions/`?"*
- Answer is one of: `No — local concern` (with one sentence why) or `Yes — RD-<id>-<slug>.md` (with the change made).

This is enforced by `AGENTS.md` (Feature documentation workflow). The gate exists so the question is never silently skipped.

## Phase 0 — Branch hygiene (pre-implementation)

Status: `[x]` done

- [x] **H** `feature/lead-management-platform` merged into `dev` (commit `26a3149`); `feature/dev-hosting-security-hardening` cut from updated `dev`; phase work happens on `phase/dev-hosting-security-phase-1` off the feature parent.
- [x] No unrelated WIP — the previous WIP files were already committed on `feature/lead-management-platform` (commits `89a5674`, `8f708b6`) before merge.

No `phase-0-implementation.md` — pre-implementation only.

## Phase 1 — Hardened `prod` profile + pre-deploy verification (A3, A5, A6, A7)

Status: `[x]` done — see [`phase-1-implementation.md`](./phase-1-implementation.md).

The cheap, low-risk wins. Nothing in this phase touches authentication or runtime auth code.

### Slice 1A — `application-prod.properties` (A3 + A7)

- [x] **M** Created `src/main/resources/application-prod.properties` with body cap (10 MB) + log hardening per [plan.md § A3](./plan.md#a3--tomcat-body-size-cap-10-mb).
- [x] Confirmed `scripts/run-app.sh prod` activates the file (`SPRING_PROFILES_ACTIVE=prod` set at line 407; no script change needed).

### Slice 1B — `.gitignore` audit (A5)

- [x] **L** Visually confirmed `.gitignore:38,41-43` already cover `.env*` and `logs/`. No change made.

### Slice 1C — Devtools exclusion verify (A6)

- [x] **M** Ran `./mvnw clean package -DskipTests` → `jar tf target/automation-engine-*.jar | grep -i devtools` returned empty. 57 MB packaged jar contains no devtools classes.
- [-] `<excludeDevtools>true</excludeDevtools>` in `pom.xml` not needed — already absent from the packaged jar.

### Phase gate

- [x] `./mvnw clean test` → **365 tests, 0 failures, 0 errors, 36 skipped** (≫ 85% required by `developer-rules.md`).
- [-] Live large-body curl smoke against `/webhooks/fub` deferred to deploy-time. Justification: this phase only adds a properties file; live smoke requires a running instance with FUB signing key configured. To be re-confirmed in Phase 4 housekeeping.
- [x] **Repo decisions impact**: `No` — properties tuning is a local feature concern.

### Plan correction logged for Phase 4

The plan's verification commands say `./mvnw -P prod ...` — `-P` is a *Maven* profile flag, but there is no Maven profile named `prod` in `pom.xml`. Maven prints a warning and runs anyway. The correct invocation is plain `./mvnw clean test`; the `prod` Spring profile is activated at runtime via `SPRING_PROFILES_ACTIVE=prod`. Phase 4 will correct the verification snippets in `plan.md`.

## Phase 2 — Backend auth foundation (A1a)

Status: `[x]` done — see [`phase-2-implementation.md`](./phase-2-implementation.md).

The full backend half of A1: schema, JWT plumbing, security config, login/me endpoints, role-based authz on every admin controller, seeder, and full test coverage. Landed as one cohesive backend-only commit; the SPA is unchanged in this phase.

### Slice 2A — Schema + persistence

- [x] **H** Added Flyway migration `V17__create_app_user_table.sql` (V16 was already taken; plan said V16, log records the bump).
- [x] Added `AppUserRole` enum and `AppUserEntity`.
- [x] Added `AppUserRepository` (`findByUsernameIgnoreCase`, `touchLastLogin`).
- [x] Tests: `AppUserRepositoryTest` (`@DataJpaTest`) — 4 tests passing.

### Slice 2B — JWT plumbing

- [x] **H** Added `spring-boot-starter-security`, `jjwt-api`/`-impl`/`-jackson` 0.12.6, `spring-security-test` to `pom.xml`.
- [x] Added `JwtProperties` (`@ConfigurationProperties(prefix = "admin.auth.jwt")`).
- [x] Added `JwtKeyFactory` — fails startup on blank secret outside `local`; falls back to a random key with `WARN` in `local`; rejects secrets shorter than 32 chars unconditionally.
- [x] Added `JwtService` with `issue(...)` / `parse(...)`.
- [x] Added `JwtPrincipal` record.
- [x] Tests: `JwtServiceTest` — 6 tests passing (round-trip, expiry, tampering, wrong issuer, foreign key, unknown role).

### Slice 2C — Security config + filter

- [x] **H** Added `SecurityConfig` (`@EnableWebSecurity`, `@EnableMethodSecurity`, stateless, CSRF disabled, `BCryptPasswordEncoder(12)`).
- [x] Added `JwtAuthenticationFilter` (skipped on `/webhooks/**` and `/health`).
- [x] Added `JsonAuthEntryPoint` (401 JSON, no redirect).
- [x] Added `AppUserDetailsService` over `AppUserRepository`.
- [x] Tests: `SecurityConfigTest` — 9 tests passing (anonymous/authenticated/role outcomes).
- [-] `JwtAuthenticationFilterTest` deferred — filter behavior is exercised end-to-end through `SecurityConfigTest` and `AdminAuthControllerTest`. Coverage is sufficient; an isolated unit test would duplicate them.

### Slice 2D — Login/me endpoints + seeder

- [x] **H** Added `AdminAuthController` (`POST /admin/auth/login`, `GET /admin/auth/me`).
- [x] Added `AdminAuthService` (owns `last_login_at` updates and "active user" lookup).
- [x] Added `AdminAuthProperties` (`@ConfigurationProperties(prefix = "admin.auth")`).
- [x] Added `AdminUserSeeder` (one-shot env-var seed; fail-fast in `prod` when blank; warn-and-skip in `local`/no-profile).
- [x] Added properties to `application.properties` and entries to `.env.example`.
- [x] Tests: `AdminAuthControllerTest` (6 tests passing) + `AdminUserSeederTest` (5 tests passing).

### Slice 2E — Role-based authz on existing admin controllers (A1.6)

- [x] **H** Added `@PreAuthorize` per the role table in [plan.md § A1.6](./plan.md#a16--role-based-endpoint-authorization). Applied class-level baseline `hasAnyRole('ADMIN','OPERATOR','VIEWER')` and method-level overrides on:
  - `AdminWorkflowController` — ADMIN-only on create/update/activate/deactivate/rollback/archive; OPERATOR+ on validate.
  - `AdminWorkflowRunController` — OPERATOR+ on cancel.
  - `ProcessedCallAdminController` — OPERATOR+ on replay.
  - `AdminLeadController`, `AdminWebhookController` — read-only baseline (all roles).
- [x] Updated existing tests with `@WithMockUser(username = "admin-test", roles = "ADMIN")` at class level: `AdminWebhookControllerTest`, `AdminWorkflowControllerTest`, `AdminWorkflowRunControllerTest`, `WorkflowAdminApiIntegrationTest`, `AdminLeadsFlowTest`, `AdminProcessedCallsFlowTest`, `AdminProcessedCallsPostgresRegressionTest`, `AdminWebhooksFlowTest`, `AdminWebhooksPostgresRegressionTest`, `PolicyAdminApiCutoverIntegrationTest`. Added `@MockitoBean JwtService` to `@WebMvcTest` cases (`AdminWorkflowRunControllerTest`, `WebhookIngressControllerTest`).
- [x] `AdminWebhooksStreamFlowTest` (RANDOM_PORT) updated to issue a real JWT in `@BeforeEach` and pass it to `StreamReader.open(...)`.
- [x] Role-mismatch tests live in `SecurityConfigTest`: VIEWER→workflow activate→403, OPERATOR→workflow activate→403, ADMIN→reachable, OPERATOR→cancel-run→reachable, VIEWER→cancel-run→403.
- [x] Added `MockMvcSecurityTestConfig` (`src/test/java/.../support/`) restoring the `springSecurity()` MockMvc configurer that Spring Boot 4 stopped auto-applying.

### Phase gate

- [x] `./mvnw clean test` → **395 tests, 0 failures, 0 errors, 36 skipped** (≫ 85% required by `developer-rules.md`).
- [x] All new tests in this phase pass individually.
- [-] Manual curl walk-through from [plan.md § Verification](./plan.md#verification-end-to-end-post-a1) deferred to post-deploy smoke (Phase 4 housekeeping). Coverage is currently exercised by automated tests through `MockMvc`.
- [x] **Repo decisions impact**: see [phase-2-implementation.md § Repo decisions impact](./phase-2-implementation.md#repo-decisions-impact). Recommended `Yes — RD-004-admin-auth-uses-jwt-bearer.md` (proposed; not yet promoted pending operator review).

## Phase 3 — Frontend auth integration (A1b)

Status: `[x]` done — see [`phase-3-implementation.md`](./phase-3-implementation.md).

SPA changes that consume the backend from Phase 2.

### Slice 3A — Token store

- [x] **H** Added `ui/src/modules/auth/state/tokenStore.ts` (in-memory + `sessionStorage` mirror, expiry-on-read).
- [x] Vitest test `auth-token-store.test.ts` (6 tests passing).

### Slice 3B — Login page

- [x] **H** Added `ui/src/modules/auth/ui/LoginPage.tsx` + `ui/src/modules/auth/data/authClient.ts`. Registered `/admin-ui/login` in `app/router.tsx`.
- [x] Added `login` to `ui/src/shared/constants/routes.ts`.
- [x] On success, calls `tokenStore.setToken(...)` and navigates to `?next=` (validated to stay inside `/admin-ui/...`) or the dashboard.
- [x] Vitest test `auth-login-page.test.tsx` (5 tests passing).

### Slice 3C — Authenticated HTTP client + 401 redirect

- [x] **H** Updated [`httpJsonClient.ts`](../../../ui/src/platform/adapters/http/httpJsonClient.ts) to attach `Authorization: Bearer <token>` for `/admin/**` calls except `/admin/auth/login`, and on `401` clear the token + dispatch a custom `admin-auth:unauthorized` event. The event keeps the client framework-agnostic; the route guard handles navigation.
- [x] Vitest tests in `http-json-client.test.ts` (8 total — extended from 2 baseline).

### Slice 3D — Route guard + RoleGate

- [x] **M** Added `ui/src/modules/auth/ui/AuthGuard.tsx` wrapping all protected admin routes inside the existing `SessionGuard`. Redirects to `/admin-ui/login?next=<path>` when no valid token is held, and listens for `admin-auth:unauthorized` for runtime 401s.
- [x] Added `<RoleGate allow="ADMIN">` (also accepts a list) for hiding role-restricted controls in the SPA. UI gate is convenience only; the backend `@PreAuthorize` enforcement remains authoritative.
- [x] Vitest tests `auth-guard.test.tsx` (3 tests) + `auth-role-gate.test.tsx` (4 tests).

### Phase gate

- [x] `npm run lint` clean.
- [x] `npm run build` succeeds (`tsc -b && vite build`).
- [x] `npm test` (vitest run) → **342 / 342 passing** (up from 326 baseline; 16 new tests).
- [-] Live manual UI walk-through deferred to deploy-time smoke; coverage today is via the test suite.
- [x] **Repo decisions impact**: `No` — the SPA wiring is the consumer of the auth pattern already promoted in `RD-004`. No new repo-wide decision; everything here follows the contract that document established.

## Phase 4 — Documentation finalization

Status: `[x]` done — see [`phase-4-implementation.md`](./phase-4-implementation.md).

- [x] **M** Rewrote [`dev-hosting-security-checklist.md`](../../hosting-decision/dev/dev-hosting-security-checklist.md): A1/A3/A5/A6/A7 marked done with phase references; A2 and A4 moved to a new "Known issues — accepted for dev, revisit when…" section with explicit `Accepted because` and `Revisit when` triggers.
- [x] Added "Hosted dev environment" section to `README.md` with the env-var contract and pre-deploy verification.
- [x] Fixed the lingering `./mvnw -P prod ...` snippets in `plan.md` (replaced with `./mvnw ...` + `SPRING_PROFILES_ACTIVE=prod` and added a clarifying note).
- [x] Added a vertical end-to-end lifecycle diagram to `plan.md` showing which files / methods are invoked in the login + admin-call + 401 paths.
- [x] Updated `AGENTS.md` to require the lifecycle diagram for every feature plan going forward.
- [x] Phase implementation logs (1, 2, 3) already up-to-date (rewritten as decision narratives during the doc-cleanup commit).

### Phase gate

- [x] All checklist items in `dev-hosting-security-checklist.md` are either ticked or moved to the known-issues section.
- [x] `Docs/features/dev-hosting-security-hardening/phases.md` (this file) reflects all `[x]` for Phases 0–4.
- [x] Backend test suite still passes (`./mvnw clean test`); UI suite still passes (`npm test`). No code changes in this phase.
- [x] **Repo decisions impact**: `No` — Phase 4 is doc-only, codifies the lifecycle-diagram requirement in `AGENTS.md` (a workflow rule, not a code-architecture decision). The auth pattern itself is already in `RD-004`.
