# Phase 2 Implementation — Backend auth foundation (A1a)

## Status

`DONE` (2026-05-01)

## Scope

Backend half of [`plan.md § A1`](./plan.md#a1--db-backed-users--jwt-auth--role-based-authz):
- DB-backed users (`app_user` table, `AppUserRole` enum).
- JWT bearer auth via jjwt 0.12.6 (HS256), with key derivation that fails fast in deployed profiles and falls back to an ephemeral key in local dev.
- Spring Security with stateless `SecurityFilterChain`, `JwtAuthenticationFilter`, JSON 401 entry point.
- `AdminAuthController` with `POST /admin/auth/login` and `GET /admin/auth/me`.
- `AdminUserSeeder` — one-shot first-boot seed from `ADMIN_AUTH_USERNAME` / `ADMIN_AUTH_PASSWORD`.
- `@PreAuthorize` on every existing admin controller per the role table in `plan.md § A1.6`.
- Test suite-wide updates so existing admin-facing tests authenticate via `@WithMockUser`.

The SPA is unchanged in this phase; Phase 3 picks that up.

## Branching

- Feature parent: `feature/dev-hosting-security-hardening`.
- Phase branch: `phase/dev-hosting-security-phase-2` (cut from feature parent).

## Implementation log

### Slice 2A — schema + persistence

- Plan said V16; V16 was already taken (`V16__add_step_state_to_workflow_run_steps.sql`). Used **V17** instead.
- New: `src/main/resources/db/migration/V17__create_app_user_table.sql`
- New: `persistence/entity/AppUserRole`, `persistence/entity/AppUserEntity`, `persistence/repository/AppUserRepository`.
- Test: `integration/AppUserRepositoryTest` (4 tests passing) — case-insensitive lookup, duplicate-username constraint, `touchLastLogin` round-trip.

### Slice 2B — JWT plumbing

- `pom.xml` adds: `spring-boot-starter-security`, `jjwt-api 0.12.6` (compile), `jjwt-impl 0.12.6` (runtime), `jjwt-jackson 0.12.6` (runtime), `spring-security-test` (test).
- New: `config/JwtProperties` — `secret`, `issuer` (default `automation-engine`), `expiry` (default `8h`).
- New: `service/auth/JwtKeyFactory` — `@Configuration` producing a `SecretKey` bean. Policy:
  - Blank secret outside `local` → `IllegalStateException` at startup.
  - Blank secret in `local` → 64-byte random key with a `WARN` log; tokens won't survive restart.
  - Any non-blank secret < 32 chars → `IllegalStateException` (HS256 minimum).
- New: `service/auth/JwtService` — `issue(AppUserEntity)` produces an `IssuedToken(token, expiresAt)`; `parse(String)` returns a `JwtPrincipal` and validates signature + expiry + issuer + role claim.
- New: `service/auth/JwtPrincipal` record.
- Test: `service/auth/JwtServiceTest` (6 tests passing) — round-trip, expired-token rejection, tampered signature, foreign-key signature, wrong issuer, unknown role.

### Slice 2C — security config + filter

- New: `config/SecurityConfig` — `@EnableWebSecurity @EnableMethodSecurity(prePostEnabled = true)`. CSRF disabled; `SessionCreationPolicy.STATELESS`. PermitAll on `/webhooks/**`, `/health`, `/admin/auth/login`; everything else under `/admin/**` requires authentication. `BCryptPasswordEncoder(12)` and exposed `AuthenticationManager` bean.
- New: `config/security/JwtAuthenticationFilter` — `OncePerRequestFilter`. Reads `Authorization: Bearer …`; on parse failure returns `401 application/json {"error":"invalid_token"}`. Skipped via `shouldNotFilter` on `/webhooks/**` and `/health`.
- New: `config/security/JsonAuthEntryPoint` — `401 application/json {"error":"unauthorized"}`.
- New: `service/auth/AppUserDetailsService` — `UserDetailsService` over `AppUserRepository`; maps `enabled` and `ROLE_<role>` authority.
- Test: `config/SecurityConfigTest` (9 tests passing) — anonymous/authenticated/role outcomes for the matrix.

### Slice 2D — login + seeder

- New: `controller/AdminAuthController` — `POST /admin/auth/login` (returns `{token, tokenType, expiresAt, username, role}` or 401 with generic `{"error":"invalid_credentials"}`), `GET /admin/auth/me`.
- New: `service/auth/AdminAuthService` — `last_login_at` bookkeeping and active-user lookup.
- New: `config/AdminAuthProperties` — `seedUsername`, `seedPassword`.
- New: `service/auth/AdminUserSeeder` (`ApplicationRunner`) — seeds one ADMIN if `app_user` is empty AND seed creds are set; fails fast in `prod` when blank; warns and skips in `local`/no-profile.
- Properties: added `admin.auth.jwt.*` and `admin.auth.seed-*` to `application.properties` (env-driven).
- Properties (test): added a 32+ char `JWT_SECRET` to `src/test/resources/application.properties` so tests boot the context cleanly.
- `.env.example` extended with `JWT_SECRET`, `JWT_ISSUER`, `JWT_EXPIRY`, `ADMIN_AUTH_USERNAME`, `ADMIN_AUTH_PASSWORD`.
- Tests: `controller/AdminAuthControllerTest` (6 passing), `service/auth/AdminUserSeederTest` (5 passing — empty-table seeds, populated-table no-op, blank in local warns, blank in prod fails fast).

### Slice 2E — role-based authz on existing admin controllers + test updates

- `@PreAuthorize` applied per the plan's role table:
  - `AdminWorkflowController` — class-level `hasAnyRole('ADMIN','OPERATOR','VIEWER')`. Method overrides: `hasRole('ADMIN')` on create/update/activate/deactivate/rollback/archive; `hasAnyRole('ADMIN','OPERATOR')` on validate.
  - `AdminWorkflowRunController` — class-level baseline; `hasAnyRole('ADMIN','OPERATOR')` on cancel.
  - `ProcessedCallAdminController` — class-level baseline; `hasAnyRole('ADMIN','OPERATOR')` on replay.
  - `AdminLeadController`, `AdminWebhookController` — class-level baseline only (all reads).
- Existing admin-facing tests updated with class-level `@WithMockUser(username = "admin-test", roles = "ADMIN")`:
  - `controller/AdminWebhookControllerTest`, `controller/AdminWorkflowControllerTest`, `controller/AdminWorkflowRunControllerTest`, `controller/WorkflowAdminApiIntegrationTest`.
  - `integration/AdminLeadsFlowTest`, `AdminProcessedCallsFlowTest`, `AdminProcessedCallsPostgresRegressionTest`, `AdminWebhooksFlowTest`, `AdminWebhooksPostgresRegressionTest`, `PolicyAdminApiCutoverIntegrationTest`.
- `@MockitoBean JwtService` added to the two `@WebMvcTest` slices (`AdminWorkflowRunControllerTest`, `WebhookIngressControllerTest`) to satisfy the security-filter-chain bean wiring.
- `AdminWebhooksStreamFlowTest` (RANDOM_PORT) — autowires `JwtService`, mints a real ADMIN token in `@BeforeEach`, and threads it through `StreamReader.open(url, bearerToken)`.
- New: `support/MockMvcSecurityTestConfig` — `@Configuration` producing a `MockMvcBuilderCustomizer` that re-applies `SecurityMockMvcConfigurers.springSecurity()`. **Necessary because Spring Boot 4 dropped the previously-auto `MockMvcSecurityConfiguration`.** Without this bean, `@WithMockUser` writes to `TestSecurityContextHolder` but MockMvc requests don't pick it up — every admin endpoint 401s.

## Validation

- `./mvnw clean test` → **395 tests, 0 failures, 0 errors, 36 skipped** (`BUILD SUCCESS`, ~21 s).
- New tests in this phase: 30 (4 + 6 + 9 + 6 + 5 = 30 added vs. the 365 prior baseline).
- Per-test runs (single-test invocations) all green individually.

Manual `curl` end-to-end walkthrough deferred to deploy-time smoke; coverage today is via `MockMvc`.

## Plan corrections discovered

| # | Correction | Logged for Phase 4? |
|---|---|---|
| 1 | Plan said Flyway V16; actual next free is V17. | yes — update `plan.md § A1.1` and `Critical files`. |
| 2 | Plan called for a separate `JwtAuthenticationFilterTest`. Filter logic is fully exercised end-to-end through `SecurityConfigTest` + `AdminAuthControllerTest`; standalone unit test would duplicate. | yes — update `plan.md § A1.10` to reflect the deferral. |
| 3 | Spring Boot 4 dropped `MockMvcSecurityConfiguration` auto-config. The plan implicitly assumed `@WithMockUser` would just work; in practice `MockMvcSecurityTestConfig` is required. | yes — note the test infra requirement in `plan.md`. |
| 4 | The plan's `./mvnw -P prod ...` snippets are still wrong (carried forward from Phase 1). | yes — same Phase 4 housekeeping item already logged. |

## Files changed

**New (backend):**
- `src/main/resources/db/migration/V17__create_app_user_table.sql`
- `src/main/java/com/fuba/automation_engine/persistence/entity/AppUserRole.java`
- `src/main/java/com/fuba/automation_engine/persistence/entity/AppUserEntity.java`
- `src/main/java/com/fuba/automation_engine/persistence/repository/AppUserRepository.java`
- `src/main/java/com/fuba/automation_engine/config/JwtProperties.java`
- `src/main/java/com/fuba/automation_engine/config/AdminAuthProperties.java`
- `src/main/java/com/fuba/automation_engine/config/SecurityConfig.java`
- `src/main/java/com/fuba/automation_engine/config/security/JwtAuthenticationFilter.java`
- `src/main/java/com/fuba/automation_engine/config/security/JsonAuthEntryPoint.java`
- `src/main/java/com/fuba/automation_engine/service/auth/JwtKeyFactory.java`
- `src/main/java/com/fuba/automation_engine/service/auth/JwtService.java`
- `src/main/java/com/fuba/automation_engine/service/auth/JwtPrincipal.java`
- `src/main/java/com/fuba/automation_engine/service/auth/AppUserDetailsService.java`
- `src/main/java/com/fuba/automation_engine/service/auth/AdminAuthService.java`
- `src/main/java/com/fuba/automation_engine/service/auth/AdminUserSeeder.java`
- `src/main/java/com/fuba/automation_engine/controller/AdminAuthController.java`

**New (test):**
- `src/test/java/com/fuba/automation_engine/integration/AppUserRepositoryTest.java`
- `src/test/java/com/fuba/automation_engine/service/auth/JwtServiceTest.java`
- `src/test/java/com/fuba/automation_engine/service/auth/AdminUserSeederTest.java`
- `src/test/java/com/fuba/automation_engine/controller/AdminAuthControllerTest.java`
- `src/test/java/com/fuba/automation_engine/config/SecurityConfigTest.java`
- `src/test/java/com/fuba/automation_engine/support/MockMvcSecurityTestConfig.java`

**Modified (backend):**
- `pom.xml` — security + jjwt + spring-security-test deps.
- `src/main/resources/application.properties` — `admin.auth.*`.
- `.env.example` — JWT + seed creds.
- `src/main/java/com/fuba/automation_engine/controller/AdminLeadController.java` — `@PreAuthorize`.
- `src/main/java/com/fuba/automation_engine/controller/AdminWebhookController.java` — `@PreAuthorize`.
- `src/main/java/com/fuba/automation_engine/controller/ProcessedCallAdminController.java` — `@PreAuthorize` (class + replay).
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowController.java` — `@PreAuthorize` (class + 8 method-level overrides).
- `src/main/java/com/fuba/automation_engine/controller/AdminWorkflowRunController.java` — `@PreAuthorize` (class + cancel).

**Modified (test):**
- `src/test/resources/application.properties` — added `admin.auth.jwt.*`.
- 10 admin controller / integration test classes — class-level `@WithMockUser`.
- `src/test/java/com/fuba/automation_engine/controller/AdminWorkflowRunControllerTest.java` — `@MockitoBean JwtService` + `@WithMockUser`.
- `src/test/java/com/fuba/automation_engine/controller/WebhookIngressControllerTest.java` — `@MockitoBean JwtService`.
- `src/test/java/com/fuba/automation_engine/integration/AdminWebhooksStreamFlowTest.java` — JWT mint + bearer header.

## Repo decisions consulted

Per `AGENTS.md` mandatory read order:
1. `Docs/repo-decisions/README.md` — reviewed.
2. `RD-001`, `RD-002`, `RD-003` — reviewed; all relate to lead/event contracts. None constrain auth/security plumbing. No conflicts; no promotion to repo-decisions.
