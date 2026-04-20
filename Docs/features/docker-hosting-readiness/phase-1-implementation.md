# Phase 1 Implementation — Docker Hosting Readiness

Date: 2026-04-20
Status: Completed

## Planned Steps
- [x] Add multi-stage Dockerfile for UI + backend packaging.
- [x] Add `.dockerignore` for lean build context.
- [x] Add Spring `/admin-ui/*` SPA fallback endpoint.
- [x] Add hosted FUB webhook URL sync script (`PUBLIC_BASE_URL` based).
- [x] Add tests:
  - [x] Spring fallback route behavior (`AdminUiControllerTest`)
  - [x] hosted webhook sync script contract (`FubWebhookSyncScriptTest`)
- [x] Update hosting decision docs with Docker prep/ops guidance.

## Implemented Notes
- Added `Dockerfile` (node build stage + maven build stage + temurin runtime stage) to package UI and backend into one image.
  - UI build output is copied into Spring static root so hosted mode serves both `/` (landing) and `/admin-ui/*` routes.
- Added `.dockerignore` to keep build context minimal and avoid local secret/log inclusion.
- Added `AdminUiController` to forward known `/admin-ui` routes to `/admin-ui/index.html` in hosted mode.
- Added `application-hosted.properties` for hosted noise reduction (`spring.jpa.show-sql=false` and no SQL formatting).
- Added hosted webhook sync script: `scripts/fub-webhook-sync.sh`.
  - Reads managed events from `config/fub-webhook-events.txt`.
  - Sync target is `${PUBLIC_BASE_URL}/webhooks/fub`.
  - Supports `--dry-run`.
- Updated host/deploy docs in:
  - `Docs/hosting-decision/dev/dev-phase-hosting-comparison.md`
  - `README.md`

## Validation Executed
- New tests:
  - `./mvnw -Dtest=AdminUiControllerTest,FubWebhookSyncScriptTest test` ✅
- Existing backend suite:
  - `./mvnw clean test` ✅
- Existing frontend suite:
  - `cd ui && npm run lint && npm run test && npm run build` ✅
- Docker build:
  - `docker build -t automation-engine:local .` ✅
