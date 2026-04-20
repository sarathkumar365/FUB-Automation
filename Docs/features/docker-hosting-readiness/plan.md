# Docker Hosting Readiness — Plan

Date: 2026-04-20

## Goal
Create a minimal, repeatable Docker deployment path for hosted dev/internal environments without changing domain behavior.

## Approved Implementation
1. Add multi-stage `Dockerfile` and `.dockerignore`.
2. Add Spring controller mapping for `/admin-ui` deep-link fallback to `/admin-ui/index.html` while excluding API routes and static assets.
3. Add hosted webhook sync script (`scripts/fub-webhook-sync.sh`) with dry-run mode and managed-events file support.
4. Add tests for fallback routing and script contract.
5. Update hosting docs and feature phase docs.

## Acceptance Criteria
- Docker image builds backend + UI successfully.
- Deep links like `/admin-ui/workflows` resolve to SPA index in server mode.
- `/admin/*`, `/webhooks/*`, `/health` remain unaffected.
- Hosted webhook sync can upsert URLs to `{PUBLIC_BASE_URL}/webhooks/fub` for managed events.
- New tests pass along with existing suites run for touched layers.
