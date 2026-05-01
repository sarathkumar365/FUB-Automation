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

## Phase 3 — Frontend auth integration (A1b)

Status: `[ ]` todo

SPA changes that consume the backend from Phase 2.

### Slice 3A — Token store

- [ ] **H** Add `ui/src/modules/auth/state/tokenStore.ts` (in-memory + `sessionStorage` mirror, expiry-on-read).
- [ ] Vitest tests for `setToken` / `getToken` / `clearToken` / expiry behavior.

### Slice 3B — Login page

- [ ] **H** Add `ui/src/modules/auth/ui/LoginPage.tsx` and register `/admin-ui/login` in routes.
- [ ] Add `login` to `ui/src/shared/constants/routes.ts`.
- [ ] On success, call `tokenStore.setToken(...)` and navigate to `?next=` or `/admin-ui`.

### Slice 3C — Authenticated HTTP client + 401 redirect

- [ ] **H** Update [`httpJsonClient.ts`](../../../ui/src/platform/adapters/http/httpJsonClient.ts) to set `Authorization: Bearer <token>` on `/admin/**` calls (excluding `/admin/auth/login`), and on `401` clear the token + navigate to `/admin-ui/login?next=<path>`.
- [ ] Vitest test for 401 redirect.

### Slice 3D — Route guard + RoleGate

- [ ] **M** Top-level App component: redirect to `/admin-ui/login` on first render if `tokenStore.getToken()` is missing/expired and the path is under `/admin-ui/**`.
- [ ] Add `<RoleGate role="ADMIN">` and apply to ADMIN-only controls (workflow activate/deactivate/rollback buttons in admin workflow UI).

### Phase gate

- [ ] `cd ui && npm run lint && npm run build && npm run test` passes.
- [ ] Manual UI walk-through:
  - logged-out user redirected to login;
  - admin login → original page reachable, ADMIN buttons visible;
  - VIEWER login → ADMIN buttons hidden via `<RoleGate>`;
  - JWT expiry → next admin call redirects to login.

## Phase 4 — Documentation finalization

Status: `[ ]` todo

- [ ] **M** In `Docs/hosting-decision/dev/dev-hosting-security-checklist.md`: move A2 and A4 to a new "Known issues — accepted for dev, revisit when…" section. Each retains description + proposed fix; adds **Accepted because** and **Revisit when** lines per [plan.md § Deferred](./plan.md#deferred--a2-and-a4-tracked-as-known-issues).
- [ ] Mark A1, A3, A5, A6, A7 as done with PR/commit references.
- [ ] Add a short "Hosted dev environment" section to `README.md` with the env-var contract (`JWT_SECRET`, `JWT_EXPIRY`, `ADMIN_AUTH_USERNAME`, `ADMIN_AUTH_PASSWORD`).
- [ ] Update each `phase-<n>-implementation.md` log with final status + validation evidence per `AGENTS.md`.

### Phase gate

- [ ] All checklist items in `dev-hosting-security-checklist.md` are either ticked or moved to the known-issues section.
- [ ] `Docs/features/dev-hosting-security-hardening/phases.md` (this file) reflects all `[x]` for Phases 0–4.
