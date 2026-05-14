# Research

## Feature
`dev-hosting-security-hardening`

## Goal
The Automation Engine is going to be hosted on a public dev URL (Render free tier or similar) so that Follow Up Boss can deliver real webhooks to it. Today the app is wired only for local/tunnel showcase use. Before exposing it to the open internet — even as a dev environment — close the security gaps that an unauthenticated stranger can actually exploit, while explicitly accepting (and tracking) gaps that don't matter for a single-admin dev host.

## Scope (in this feature)
- **A1** — Authentication on `/admin/**`: DB-backed users + JWT bearer + role-based authorization (`ADMIN` / `OPERATOR` / `VIEWER`). Includes a small SPA login page and a token store.
- **A3** — Tomcat body-size cap (10 MB outer wall) on `application-prod.properties` so anonymous webhook posts cannot OOM the JVM on a low-RAM host.
- **A5** — `.gitignore` covers `.env*` and `logs/`. *Already satisfied by `.gitignore:38,41-43`* — verification only.
- **A6** — Confirm `spring-boot-devtools` is excluded from the deployed jar.
- **A7** — Disable `spring.jpa.show-sql` in the deployed environment (PII hygiene).

## Out of scope (deferred — known issues)
- **A2** — SSRF guard on workflow HTTP steps (`HttpRequestWorkflowStep`, `SlackNotifyWorkflowStep`).
- **A4** — Bounded HTTP response reads in `WorkflowRestHttpClientAdapter` and `AiCallServiceHttpClientAdapter`.

Both are tracked in `Docs/hosting-decision/dev/dev-hosting-security-checklist.md` under a new "Known issues — accepted for dev, revisit when…" section. Re-evaluate when:
- A2: a non-trusted user gains workflow-edit rights (today only the seeded `ADMIN` operator has it).
- A4: the codebase calls untrusted HTTP upstreams (today only Slack, FUB, our AI service).

## Source of the seven items
The numbered checklist (A1–A7) was produced during a manual security review of `feature/lead-management-platform` and persisted to `Docs/hosting-decision/dev/dev-hosting-security-checklist.md`. That doc is the upstream artifact this feature delivers against.

## Relevant accepted repo decisions
None of `RD-001` (normalized lead-event contract), `RD-002` (event-catalog state/routing), or `RD-003` (lead-identity mapping boundary) constrain auth/security plumbing. Read for context, no conflicts, no overrides needed.

## Current state (what exists today)

### Auth surface (A1)
- `pom.xml` — no `spring-boot-starter-security`, no Spring Security on the classpath.
- No `SecurityFilterChain`, `WebMvcConfigurer`, `OncePerRequestFilter`, or `HandlerInterceptor` anywhere in `src/main/java`. Greenfield.
- All admin endpoints are anonymous:
  - `AdminLeadController` (`/admin/leads/**`)
  - `AdminWebhookController` (`/admin/webhooks/**` including SSE `/stream`)
  - `ProcessedCallAdminController` (`/admin/processed-calls/**`, including replay POST)
  - `AdminWorkflowController` (`/admin/workflows/**`, including activate/deactivate/rollback)
  - `AdminWorkflowRunController` (`/admin/workflow-runs/**`, including cancel POST)
- `WebhookIngressController` (`/webhooks/**`) and `HealthController` (`/health`) must remain anonymous; FUB posts without credentials and platform health probes need open access.

### Frontend coupling (A1)
- [ui/src/platform/adapters/http/httpJsonClient.ts](../../../ui/src/platform/adapters/http/httpJsonClient.ts) — uses plain `fetch`, no `Authorization` header, no `credentials` option. All admin calls use relative paths (`/admin/...`).
- Routes constants in [ui/src/shared/constants/routes.ts](../../../ui/src/shared/constants/routes.ts) — no login route today.
- Module-per-feature layout under `ui/src/modules/{dashboard,leads,workflow-runs,...}` — `auth` module to be added.

### Profiles (A3, A7)
- Only `src/main/resources/application.properties` exists; **no `application-prod.properties` yet**.
- `scripts/run-app.sh` activates `local` for dev mode and `prod` for prod mode, but the `prod` profile has had nothing to load.
- `spring.jpa.show-sql=true` is hard-coded in the base properties (PII-leaky).

### Body-size enforcement (A3)
- `WebhookIngressController` reads `@RequestBody String rawBody` — Spring buffers the entire body before the in-app 1 MB cap at [WebhookIngressService.java:71](../../../src/main/java/com/fuba/automation_engine/service/webhook/WebhookIngressService.java) fires.
- No Tomcat-level `max-http-form-post-size` or `max-swallow-size` set.

### Build artifact (A6)
- `spring-boot-devtools` declared in `pom.xml` as `runtime` + `optional`. The Spring Boot Maven plugin normally excludes it from the repackaged fat-jar, but worth verifying against the actual deployed artifact.

### `.gitignore` (A5)
- Lines 38, 41–43 already include `logs/`, `.env`, `.env.*`, `!.env.example`. **Already satisfied.**

## Existing patterns to reuse

| Need | Reuse |
|---|---|
| `@ConfigurationProperties` style | [config/WebhookProperties.java](../../../src/main/java/com/fuba/automation_engine/config/WebhookProperties.java), [config/WorkflowStepHttpProperties.java](../../../src/main/java/com/fuba/automation_engine/config/WorkflowStepHttpProperties.java) — Lombok `@Getter @Setter` + `@ConfigurationProperties(prefix = "...")`, optional nested static classes. |
| JPA entity + repository | [persistence/entity/WebhookEventEntity.java](../../../src/main/java/com/fuba/automation_engine/persistence/entity/WebhookEventEntity.java), [persistence/repository/WebhookEventRepository.java](../../../src/main/java/com/fuba/automation_engine/persistence/repository/WebhookEventRepository.java) — Lombok-style entity, `JpaRepository`, `@Query`-based custom finders. |
| Flyway migration | `src/main/resources/db/migration/V1..V15`. Next free is `V16__create_app_user_table.sql`. |
| Controller test (admin) | [src/test/java/.../controller/AdminWorkflowControllerTest.java](../../../src/test/java/com/fuba/automation_engine/controller/AdminWorkflowControllerTest.java) — `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvc`. |
| Adapter test (HTTP) | [src/test/java/.../client/aicall/AiCallServiceHttpClientAdapterTest.java](../../../src/test/java/com/fuba/automation_engine/client/aicall/AiCallServiceHttpClientAdapterTest.java) — `com.sun.net.httpserver.HttpServer` for in-process upstreams. |
| Frontend module | `ui/src/modules/dashboard`, `ui/src/modules/leads` — `ui/state` / `ui/queries` split, Vitest tests in `src/test`. |
| Profile activation | [scripts/run-app.sh](../../../scripts/run-app.sh) already wires `local` / `prod` — no script change needed. |

## Threat model summary (why these five, in priority order)

1. **A1 — wide-open admin surface.** Anyone with the URL today can read leads, replay calls, activate workflows, and trigger outbound calls to FUB on the operator's API key. This is the dominant risk and the reason the host can't go public without auth.
2. **A3 — anonymous webhook DoS.** `/webhooks/**` must stay anonymous (FUB posts without creds). The current 1 MB application-level cap fires *after* Spring buffers the full body. On a 512 MB Render instance, one ~500 MB body OOMs the JVM. Cheap, real, fix-with-config.
3. **A7 — PII in logs.** `show-sql=true` logs every JPA query; FUB payloads carry PII. Once dashboard logs are public to the hosting platform, this becomes a soft data leak. One config line.
4. **A6 — devtools in deployed jar (verification).** Devtools enables LiveReload and disables template caches. Spring Boot's plugin normally excludes it; this is a "trust but verify" step before first deploy.
5. **A5 — accidental secret commit.** Already prevented by `.gitignore`. Tick the box.

## Non-goals (explicit)

- Token revocation list / server-side logout endpoint with denylist (TTL-based revocation only).
- Refresh tokens (re-login on 8 h expiry is acceptable for dev).
- User CRUD endpoints, self-service password reset.
- Login-attempt rate-limiting / account lockout (TODO; logged on failure).
- MFA, password complexity rules, rotation policy.
- Distributed sessions / Redis (single-instance dev host).
- Audit log table beyond `app_user.last_login_at`.
- Streaming filter for raw JSON body limits (Tomcat outer wall is enough for dev).

## Branch / workflow note

Per `AGENTS.md`, this feature should be implemented on a new `feature/dev-hosting-security-hardening` branch cut from `dev`. All phase work commits directly to that feature branch (no phase sub-branches). Today the operator is on `feature/lead-management-platform` with unrelated uncommitted changes; switching branches is a pre-implementation step, not part of any phase.

## Repo decisions consulted (mandatory read order)
1. `Docs/repo-decisions/README.md` — reviewed.
2. `RD-001`, `RD-002`, `RD-003` — reviewed; all relate to lead/event contracts, none constrain auth/security or properties hardening. No promotion of feature decisions to repo-decisions is needed in this feature.
