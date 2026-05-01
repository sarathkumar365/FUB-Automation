# Dev Hosting — Security Checklist

Findings from the codebase security review, scoped for a publicly reachable **dev/showcase** environment (not production). Items are grouped by status: completed, accepted-as-known-issues, nice-to-haves, and verified-safe.

- Original review: 2026-04-28, branch `feature/lead-management-platform`
- Implementation: feature `dev-hosting-security-hardening` (2026-05-01)
- Reviewer: Claude (manual scan)

For implementation details see [`Docs/features/dev-hosting-security-hardening/`](../../features/dev-hosting-security-hardening/) — `plan.md` for the design (with end-to-end lifecycle diagram), `phase-N-implementation.md` for what shipped in each phase.

---

## A. Completed before dev hosting goes public

### A1. Authentication on `/admin/**` ✅
- **Status:** Done in `phase/dev-hosting-security-phase-2` + `phase/dev-hosting-security-phase-3`.
- **Shape shipped:** Spring Security stateless + JWT bearer (HS256, jjwt) + DB-backed users in `app_user` (Flyway V17). Three-role enum (`ADMIN`, `OPERATOR`, `VIEWER`) enforced per endpoint via `@PreAuthorize`. SPA login page + token store + AuthGuard + RoleGate.
- **Promoted to repo-decisions:** [`RD-004-admin-auth-uses-jwt-bearer.md`](../../repo-decisions/RD-004-admin-auth-uses-jwt-bearer.md).

### A3. Webhook body size cap before buffering ✅
- **Status:** Done in `phase/dev-hosting-security-phase-1`.
- **Shape shipped:** new `src/main/resources/application-prod.properties` sets `server.tomcat.max-http-form-post-size=10MB` and `max-swallow-size=10MB` (operator chose 10 MB, raised from the originally-proposed 2 MB). The existing `webhook.max-body-bytes=1MB` application-level cap stays as the inner wall — defense in depth.
- **Known sub-gap (documented):** Spring's `@RequestBody String` for JSON isn't strictly bounded by these properties; `max-swallow-size` is what protects the JVM. A streaming counting filter is over-scope for dev.

### A5. `.env` gitignored ✅
- **Status:** Already done; verified in Phase 1.
- **Note:** `.gitignore:38,41-43` already cover `logs/`, `.env`, `.env.*` (with `!.env.example` exception). No edit was needed.

### A6. `spring-boot-devtools` excluded from deployed jar ✅
- **Status:** Verified in Phase 1.
- **Evidence:** `./mvnw clean package -DskipTests` produces a 57 MB jar; `jar tf target/automation-engine-*.jar | grep -i devtools` returns empty. No `pom.xml` change needed. Re-run before each deploy.

### A7. `spring.jpa.show-sql` disabled in deployed env ✅
- **Status:** Done in `phase/dev-hosting-security-phase-1` (same `application-prod.properties` as A3).
- **Shape shipped:** `spring.jpa.show-sql=false` and `spring.jpa.properties.hibernate.format_sql=false` apply automatically when `SPRING_PROFILES_ACTIVE=prod`. Local dev keeps `show-sql=true` for ergonomics.

---

## B. Known issues — accepted for dev, revisit when…

These are real findings, deliberately deferred for a single-admin dev host. The risk preconditions are absent today; revisit at the trigger events listed.

### A2. SSRF guard on workflow HTTP steps (deferred)
- **Files:** `client/http/WorkflowRestHttpClientAdapter.java`, used by `HttpRequestWorkflowStep` and `SlackNotifyWorkflowStep`.
- **Risk shape:** the workflow definition supplies the URL; no host allowlist, no block on private/loopback/link-local/metadata addresses. An admin who can edit workflows could pivot to AWS metadata, internal services, etc.
- **Proposed fix:** before dispatch, resolve the host, reject non-HTTPS, reject private / loopback / link-local / metadata IPs, optional allowlist.
- **Accepted because:** today only `ADMIN` can create or edit workflows, and the seeded admin is the operator. The threat is "I attack myself," which isn't a real threat.
- **Revisit when:** any of (a) a non-trusted user is granted workflow-edit rights (OPERATOR/VIEWER promoted, second admin added, self-service registration introduced); (b) a workflow URL becomes derivable from end-user input; (c) audit logs would benefit from outbound-host visibility.

### A4. Bounded HTTP response reads (deferred)
- **Files:** `client/http/WorkflowRestHttpClientAdapter.java:83`, `client/aicall/AiCallServiceHttpClientAdapter.java`.
- **Risk shape:** `bodyStream.readAllBytes()` (and the AI-call adapter's `String` body materialization) has no ceiling. A buggy or malicious upstream returning multi-GB OOMs the JVM on a 512 MB instance.
- **Proposed fix:** cap reads at e.g. 1 MB with a bounded reader; throw `WorkflowHttpClientException("response too large", false)` past that.
- **Accepted because:** all current upstreams are trusted (Slack webhooks, FUB API, our own AI service). Realistic risk is a buggy upstream, not a malicious one.
- **Revisit when:** any of (a) workflow steps target user-supplied or untrusted upstreams; (b) a buggy upstream incident forces a hardening fix anyway; (c) memory-pressure incidents on the dev host correlate with an outbound HTTP call.

---

## C. Nice-to-have for dev hosting (not blocking)

### B1. Auth & rate limiting on the SSE live feed
- **Endpoint:** `GET /admin/webhooks/stream`, `webhook.live-feed.emitter-timeout-ms` default 30 min.
- **Status (auth):** Covered by A1 — the SSE endpoint is now under `/admin/**` auth.
- **Outstanding:** per-IP connection cap. Skip for dev; revisit if SSE connection counts become a problem.

### B2. Scrub sensitive headers and bodies from logs
- **Action:** ensure `FUB-Signature` and any auth headers are never logged; never log full webhook bodies in `INFO`. Worth a sweep before the host is exposed; not blocking.

### B3. Basic security response headers
- **Status:** A1 added Spring Security, which sets `X-Content-Type-Options`, `X-Frame-Options`, etc. by default. Confirm via curl after first deploy and add a `OncePerRequestFilter` for any missing headers.

### B4. Fail fast when webhook signing key is missing
- **Status:** Open. `FubWebhookSignatureVerifier` silently 401s when `FUB_X_SYSTEM_KEY` is blank. A startup check would surface misconfiguration loudly.

### B5. Pin and review transitive dependencies
- **Action:** run `./mvnw dependency:tree` + `./mvnw org.owasp:dependency-check-maven:check` once before exposing the host.

### B6. CORS posture
- **Status:** No CORS config exists, which is correct as long as the SPA and backend share an origin. If they're ever split, add an explicit narrow CORS config — never `*`.

---

## D. Verified safe (no action)

- **HMAC webhook verification** — `FubWebhookSignatureVerifier` uses constant-time compare and rejects when the key is blank. ✅
- **SQL injection** — all JPA repositories use named-parameter `@Query`; no string concatenation. ✅
- **Actuator** — `spring-boot-starter-actuator` is not on the classpath; nothing at `/actuator/**`. ✅
- **Flyway** — forward-only migrations; `ddl-auto=none`. ✅
- **Committed secrets** — `git log` shows no `.env` / credential file ever committed; docs use placeholders only. ✅
- **Devtools in deployed jar** — verified absent (A6). ✅

---

## Sign-off

- A1, A3, A5, A6, A7 — landed.
- A2, A4 — accepted as known-issues with explicit revisit triggers (section B).
- B1–B6 — flagged for follow-up; not blockers.

Dev host is safe to expose publicly under the assumptions in section B.
