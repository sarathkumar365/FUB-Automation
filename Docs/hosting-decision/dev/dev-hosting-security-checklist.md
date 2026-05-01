# Dev Hosting — Security Checklist

Findings from the codebase security review, scoped for a publicly reachable **dev/showcase** environment (not production). Items are grouped by whether they block exposing the app or can be deferred.

- Date: 2026-04-28
- Branch reviewed: `feature/lead-management-platform`
- Reviewer: Claude (manual scan)

---

## A. Must-have BEFORE dev hosting goes public

These all involve risks that an unauthenticated stranger on the internet can exploit. Without them, the URL must stay behind a tunnel / IP allowlist / Basic-auth proxy.

### A1. Add authentication on `/admin/**` (CRITICAL)
- **Problem:** `pom.xml` has no `spring-boot-starter-security`. There is no `SecurityFilterChain`. All admin endpoints are anonymous: `/admin/leads`, `/admin/workflows`, `/admin/workflow-runs/{id}/cancel`, `POST /admin/processed-calls/{id}/replay`, `POST /admin/workflows/{key}/activate`, etc.
- **Impact:** anyone with the URL can read leads, replay calls, and activate/deactivate workflows. Replay/activation triggers outbound calls to FUB using your real API key.
- **Fix:** add `spring-boot-starter-security` and either HTTP Basic auth (single env-driven user) or a static `X-Admin-Token` header filter. Permit `/webhooks/**` and `/health` anonymously, deny everything else.

### A2. SSRF guard on workflow HTTP steps (CRITICAL)
- **Files:** `src/main/java/com/fuba/automation_engine/client/http/WorkflowRestHttpClientAdapter.java`, used by `HttpRequestWorkflowStep` and `SlackNotifyWorkflowStep`.
- **Problem:** the workflow definition supplies the URL, with no host allowlist and no block on private/loopback/link-local/metadata addresses.
- **Impact:** combined with A1, an unauthenticated user can create a workflow targeting `http://169.254.169.254/...` (cloud metadata), `http://127.0.0.1:...`, or any internal service, then trigger it and read the response back via `/admin/workflow-runs/{id}`.
- **Fix:** before dispatch, resolve the host, reject non-HTTPS, and reject private / loopback / link-local / metadata IPs. Optionally maintain an allowlist (e.g. `hooks.slack.com`, FUB hosts).

### A3. Webhook body size cap before buffering (HIGH)
- **File:** `src/main/java/com/fuba/automation_engine/controller/WebhookIngressController.java` line 38 — `@RequestBody String rawBody`.
- **Problem:** the 1 MB `webhook.max-body-bytes` check runs after Spring has fully buffered the body. No `server.tomcat.max-http-form-post-size` / `max-swallow-size` set.
- **Impact:** unauthenticated POST to `/webhooks/fub` with a 100 MB body buffers fully before rejection — easy memory exhaustion DoS.
- **Fix:** in `application.properties` (or a `prod` profile):
  ```
  server.tomcat.max-http-form-post-size=2MB
  server.tomcat.max-swallow-size=2MB
  ```
  Ideally also stream-read the body with a hard cap.

### A4. Unbounded HTTP response read (HIGH)
- **Files:** `WorkflowRestHttpClientAdapter.java:83`, also `AiCallServiceHttpClientAdapter`.
- **Problem:** `bodyStream.readAllBytes()` with no ceiling. A malicious or buggy upstream returning multi-GB OOMs the JVM. The 512 MB Render free tier will fall over very quickly.
- **Fix:** cap reads at e.g. 1 MB with a bounded reader and surface a `WorkflowHttpClientException` if exceeded.

### A5. Add `.env` to `.gitignore` (HIGH)
- **Problem:** `.gitignore` does not list `.env` (verified). `.env` holds real `FUB_API_KEY` and `FUB_X_SYSTEM_KEY`.
- **Impact:** one accidental `git add .` ships the keys to GitHub. (No leak in history yet.)
- **Fix:** add to `.gitignore`:
  ```
  .env
  .env.local
  .env.*.local
  logs/
  ```

### A6. Confirm `spring-boot-devtools` is not in the deployed jar (MEDIUM, easy)
- **Problem:** devtools is declared `runtime` + `optional`. The Spring Boot Maven plugin normally excludes it from the repackaged fat-jar, but worth verifying for the actual deployed artifact.
- **Fix:** after `./mvnw -P prod package`, run `jar tf target/automation-engine-*.jar | grep -i devtools` — output must be empty.

### A7. Disable verbose SQL logging in deployed profile (MEDIUM)
- **File:** `src/main/resources/application.properties` line 12 — `spring.jpa.show-sql=true`.
- **Problem:** logs every query; FUB payloads carry PII (names, emails, phone numbers). Combined with public access to the host's logs (Render dashboard, etc.), this is a soft data leak.
- **Fix:** override in deployed env:
  ```
  SPRING_JPA_SHOW_SQL=false
  ```
  Or set `spring.jpa.show-sql=false` and `spring.jpa.properties.hibernate.format_sql=false` in `application-prod.properties`.

---

## B. Nice-to-have for dev hosting (do soon, not blocking)

### B1. Auth & rate limiting on the SSE live feed
- **Endpoint:** `GET /admin/webhooks/stream`, with `webhook.live-feed.emitter-timeout-ms` defaulting to 30 minutes.
- **Risk:** unauthenticated long-lived connections → easy connection exhaustion. A1 covers the auth side; also add a per-IP connection cap if exposed.

### B2. Scrub sensitive headers and bodies from logs
- **File:** `WebhookIngressController.java` line 41 logs full header count and content length per request. Other call sites log payload-related fields.
- **Action:** ensure `FUB-Signature` and any auth headers are never logged; never log full webhook bodies in `INFO`.

### B3. Add basic security response headers
- Without spring-security, Spring 4.x does not add `X-Content-Type-Options`, `X-Frame-Options`, or a default CSP. The admin SPA can be iframed → clickjacking risk.
- **Action:** A1 (adding spring-security) gives these defaults for free; otherwise add a `OncePerRequestFilter` that sets them.

### B4. Fail fast when webhook signing key is missing
- **File:** `FubWebhookSignatureVerifier.java` — when `FUB_X_SYSTEM_KEY` is blank, every webhook is silently 401'd. Currently no startup check.
- **Action:** if `webhook.sources.fub.enabled=true` and the signing key is blank, fail application startup with a clear error.

### B5. Pin and review transitive dependencies
- Run `./mvnw dependency:tree` and `./mvnw org.owasp:dependency-check-maven:check` once before going public. Spring Boot 4.0.3 is recent, so unlikely to have known CVEs, but confirm.

### B6. CORS posture
- No CORS config exists. That is correct **only if** the SPA is bundled into the Spring jar and served same-origin (Option 1 of the hosting plan). If you ever split FE and BE onto different origins, add an explicit, narrow CORS config — do not allow `*`.

---

## C. Verified safe (no action)

- **HMAC webhook verification** — `FubWebhookSignatureVerifier` uses `MessageDigest.isEqual` (constant-time) and rejects when the key is blank. Implementation is correct.
- **SQL injection** — all repositories use JPA `@Query` with named parameters; no string concatenation found.
- **Actuator** — `spring-boot-starter-actuator` is not on the classpath; nothing exposed at `/actuator/**`.
- **Flyway** — migrations are forward-only, no drop/recreate; run on startup with `ddl-auto=none`.
- **Committed secrets** — `git log` shows no `.env`/credential file ever committed; documentation references use placeholder values only.

---

## Suggested order of execution

1. A5 (gitignore) — 1 minute
2. A7 + A6 (logging + devtools verify) — 5 minutes
3. A3 + A4 (size caps) — 30 minutes
4. A1 (admin auth) — half day; biggest single risk reduction
5. A2 (SSRF guard) — half day; closes the pivot path A1 still allows for an authenticated insider
6. B1–B4 — pick up after the host is live

Once A1–A7 are merged, the dev host is safe to expose with a public URL.
