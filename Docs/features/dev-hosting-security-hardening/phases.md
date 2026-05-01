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

Status: `[ ]` todo

- [ ] **H** Confirm with operator: cut `feature/dev-hosting-security-hardening` from `dev` (or accept an explicit deviation, mirroring how `leads-admin-ui` was handled).
- [ ] Stash / commit / move unrelated changes currently on `feature/lead-management-platform` so this feature's branches start clean.

No code in this phase — it exists so that the very first commit of Phase 1 lands on the right parent. No `phase-0-implementation.md` needed.

## Phase 1 — Hardened `prod` profile + pre-deploy verification (A3, A5, A6, A7)

Status: `[ ]` todo

The cheap, low-risk wins. Nothing in this phase touches authentication or runtime auth code.

### Slice 1A — `application-prod.properties` (A3 + A7)

- [ ] **M** Create `src/main/resources/application-prod.properties` with the body cap and log hardening from [plan.md § A3](./plan.md#a3--tomcat-body-size-cap-10-mb).
- [ ] Confirm `scripts/run-app.sh prod` activates the file (no script change expected).

### Slice 1B — `.gitignore` audit (A5)

- [ ] **L** Visually confirm `.gitignore:38,41-43` already cover `.env*` and `logs/`. No change expected.

### Slice 1C — Devtools exclusion verify (A6)

- [ ] **M** Run `./mvnw -P prod clean package -DskipTests` then `jar tf target/automation-engine-*.jar | grep -i devtools`. Expected empty.
- [ ] If non-empty, add `<excludeDevtools>true</excludeDevtools>` to `spring-boot-maven-plugin` in `pom.xml`.

### Phase gate

- [ ] `./mvnw -P prod clean test` passes.
- [ ] Smoke-run `./mvnw -P prod spring-boot:run -Dspring-boot.run.profiles=prod`; large-body curl against `/webhooks/fub` returns 413/400 instead of OOMing.

## Phase 2 — Backend auth foundation (A1a)

Status: `[ ]` todo

The full backend half of A1: schema, JWT plumbing, security config, login/me endpoints, role-based authz on every admin controller, seeder, and full test coverage. Lands as one cohesive backend-only PR; the SPA is unchanged in this phase, so an optional `admin.auth.enabled` flag may be used to keep the filter chain permit-all in deployed environments until Phase 3 ships.

### Slice 2A — Schema + persistence

- [ ] **H** Add Flyway migration `V16__create_app_user_table.sql` per [plan.md § A1.1](./plan.md#a11--schema-flyway-migration).
- [ ] Add `AppUserRole` enum and `AppUserEntity` per [plan.md § A1.2](./plan.md#a12--jpa-entity--repository).
- [ ] Add `AppUserRepository` (`findByUsernameIgnoreCase`, `touchLastLogin`).
- [ ] Tests: `AppUserRepositoryTest` (`@DataJpaTest`).

### Slice 2B — JWT plumbing

- [ ] **H** Add Spring Security + jjwt deps to `pom.xml`.
- [ ] Add `JwtProperties` (`@ConfigurationProperties(prefix = "admin.auth.jwt")`).
- [ ] Add `JwtService` with `issue(...)` / `parse(...)`, secret-length validation in `prod`.
- [ ] Add `JwtPrincipal` record.
- [ ] Tests: `JwtServiceTest` (round-trip, expiry, tampering, wrong issuer).

### Slice 2C — Security config + filter

- [ ] **H** Add `SecurityConfig` (`@EnableWebSecurity`, `@EnableMethodSecurity`, stateless, CSRF disabled).
- [ ] Add `JwtAuthenticationFilter` (skipped on `/webhooks/**` and `/health`).
- [ ] Add `JsonAuthEntryPoint` (401 JSON, no redirect).
- [ ] Add `AppUserDetailsService` over `AppUserRepository`.
- [ ] Tests: `JwtAuthenticationFilterTest`, `SecurityConfigTest` (anonymous/authenticated/role outcomes).

### Slice 2D — Login/me endpoints + seeder

- [ ] **H** Add `AdminAuthController` (`POST /admin/auth/login`, `GET /admin/auth/me`).
- [ ] Add `AdminAuthService` (owns `last_login_at` updates).
- [ ] Add `AdminUserSeeder` (one-shot env-var seed; fail-fast in `prod` when blank).
- [ ] Add properties to `application.properties` and entries to `.env.example` per [plan.md § A1.9](./plan.md#a19--properties).
- [ ] Tests: `AdminAuthControllerTest`, `AdminUserSeederTest`.

### Slice 2E — Role-based authz on existing admin controllers (A1.6)

- [ ] **H** Add `@PreAuthorize` per the role table in [plan.md § A1.6](./plan.md#a16--role-based-endpoint-authorization) to every method in:
  - `AdminLeadController`
  - `AdminWebhookController`
  - `ProcessedCallAdminController`
  - `AdminWorkflowController`
  - `AdminWorkflowRunController`
- [ ] Update existing tests with `@WithMockUser(roles = "ADMIN")` at class level.
- [ ] Add at least one targeted role-mismatch test per controller (e.g. `VIEWER` → 403 on a state-changing endpoint).

### Phase gate

- [ ] `./mvnw -P prod clean test` ≥ 85% pass (per `developer-rules.md` mandatory testing policy).
- [ ] All new tests in this phase pass individually.
- [ ] Manual curl walk-through from [plan.md § Verification](./plan.md#verification-end-to-end-post-a1) succeeds for the JWT, role, and webhook/health paths.

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
