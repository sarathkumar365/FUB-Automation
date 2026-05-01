# Phase 1 Implementation — Hardened `prod` profile + pre-deploy verification

## Status

`DONE` (2026-05-01)

## Scope

Phase 1 covers the cheap, low-risk hardening items from
[`plan.md`](./plan.md):

- **A3** — Tomcat body-size cap (10 MB) so anonymous webhook posts cannot OOM the JVM on a low-RAM dev host.
- **A5** — `.gitignore` audit (already satisfied; verification only).
- **A6** — Confirm `spring-boot-devtools` is excluded from the deployed jar.
- **A7** — Disable `spring.jpa.show-sql` in deployed environments.

No authentication or runtime-auth code is touched in this phase.

## Branching

- Feature parent: `feature/dev-hosting-security-hardening` (cut from `dev` after merging `feature/lead-management-platform`, commit `26a3149`).
- Phase branch: `phase/dev-hosting-security-phase-1` (cut from feature parent).

## Implementation log

- [x] Created [`src/main/resources/application-prod.properties`](../../../src/main/resources/application-prod.properties) with:
  - `server.tomcat.max-http-form-post-size=10MB`
  - `server.tomcat.max-swallow-size=10MB`
  - `spring.servlet.multipart.max-request-size=10MB`
  - `spring.servlet.multipart.max-file-size=10MB`
  - `spring.jpa.show-sql=false`
  - `spring.jpa.properties.hibernate.format_sql=false`
- [x] Confirmed activation path: [`scripts/run-app.sh:407`](../../../scripts/run-app.sh) sets `SPRING_PROFILES_ACTIVE="${PROD_PROFILE}"` (`PROD_PROFILE="prod"` at line 11), so `./scripts/run-app.sh prod` picks the new file up. No script change required.
- [x] Verified `.gitignore:38,41-43` already cover `logs/`, `.env`, `.env.*`, with `!.env.example` exception. A5 satisfied; no edit.
- [x] Built the prod artifact and verified devtools is absent:
  ```
  ./mvnw clean package -DskipTests
  jar tf target/automation-engine-0.0.1-SNAPSHOT.jar | grep -i devtools
  ```
  Output: empty. Packaged jar size: 57 MB. A6 satisfied; no `<excludeDevtools>` change needed.

## Validation

- [x] **Backend test suite** (`./mvnw clean test`):
  - Tests run: **365**
  - Failures: **0**
  - Errors: **0**
  - Skipped: **36**
  - Duration: ~19.5 s
  - Result: `BUILD SUCCESS`
  - Pass rate ≫ 85% threshold from `developer-rules.md`.
- [x] **No new tests added.** This phase is configuration-only — no Java/TS code was changed and the new properties file does not have a fast unit-testable shape (it's a runtime override). The plan's manual verification (live curl with oversized body) is deferred to deploy-time per the phase gate notes below.

## Deferred verification (logged for later)

- **Live large-body smoke** against `/webhooks/fub` returning 413/400 — requires a running instance plus a configured FUB signing key. Will be confirmed once the dev host is provisioned (Phase 4 housekeeping or first deploy).

## Plan corrections discovered

- Several verification snippets in [`plan.md`](./plan.md) use `./mvnw -P prod ...`. `-P` is a *Maven* profile flag, and no Maven profile named `prod` exists in `pom.xml`. Maven prints a warning and runs anyway. The correct invocation for the test suite is plain `./mvnw clean test`; the `prod` Spring profile is activated at runtime via `SPRING_PROFILES_ACTIVE=prod`. Phase 4 will edit these snippets in `plan.md` (and any other docs that copy them).

## Files changed

- New: `src/main/resources/application-prod.properties`
- New (this log): `Docs/features/dev-hosting-security-hardening/phase-1-implementation.md`
- Updated: `Docs/features/dev-hosting-security-hardening/phases.md` (Phase 0 + Phase 1 marked done)

## Repo decisions consulted

Per `AGENTS.md` mandatory read order:
1. `Docs/repo-decisions/README.md` — reviewed.
2. `RD-001`, `RD-002`, `RD-003` — reviewed; all relate to lead/event contracts. None constrain configuration hardening. No conflicts; no promotion to repo-decisions.
