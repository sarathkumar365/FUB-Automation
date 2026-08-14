# Phase 1 — Hardened `prod` profile + pre-deploy verification

## Status
`DONE` (2026-05-01)

## What I was thinking

The whole feature breaks into "auth" (Phase 2) and "everything else." The "everything else" is cheap, low-risk, and unblocks Phase 2's review by making sure the deployed profile actually exists and is hardened. So Phase 1 was deliberately small: ship the prod-properties file, verify the easy stuff, build confidence in the workflow, and move on.

## Decisions taken

- **One properties file, one profile.** Rather than scatter env-var defaults through the base properties, I put everything that *only* applies in deployed environments into `application-prod.properties`. Local dev keeps `show-sql=true` for ergonomics; the prod profile turns it off and adds the body cap. `scripts/run-app.sh prod` already sets `SPRING_PROFILES_ACTIVE=prod`, so no script change.
- **10 MB outer wall, 1 MB inner wall.** The user asked for 10 MB at the Tomcat level (raised from the 2 MB I originally proposed). The existing `webhook.max-body-bytes=1048576` in-app cap stays — it enforces the actual webhook contract. The 10 MB outer wall is purely there to stop a 500 MB anonymous post from OOM'ing the JVM on a 512 MB Render instance before the app-level check fires. Two walls, two purposes; keep both.
- **A5 was already done.** `.gitignore` already covered `.env*` and `logs/`. The plan flagged this as a checklist item, not a code change. Verified visually and moved on.
- **A6 is verification, not code.** I built the prod jar and grep'd for devtools — output empty. No `pom.xml` change needed. If devtools ever leaks into the deployed jar in future, that's where to fix it.

## Surprises

The plan's verification snippets used `./mvnw -P prod ...`. That's a *Maven* profile flag, but there is no Maven profile named `prod` in `pom.xml`; Maven prints a warning and runs the suite anyway. The correct invocation is plain `./mvnw clean test`; the `prod` *Spring* profile is activated at runtime via `SPRING_PROFILES_ACTIVE`. Logged for the Phase 4 plan-cleanup pass.

## Validation

`./mvnw clean test` → **365 / 0F / 0E / 36 skipped**, `BUILD SUCCESS`. No new tests added (configuration-only phase). Live large-body curl smoke deferred to first deploy because it needs a running instance with FUB credentials.

## Repo decisions impact

`No` — local feature concern. Body-size tuning and log-level overrides are scoped to this feature's deployed environment; nothing here generalizes into a repo-wide rule.
