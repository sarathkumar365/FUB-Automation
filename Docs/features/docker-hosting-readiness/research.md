# Docker Hosting Readiness — Research

Date: 2026-04-20

## Problem
Current app is optimized for local dev (`run-app.sh dev` with Vite + cloudflared). We need a deployable Docker path for hosted dev/internal usage.

## Current State Findings
- Backend: Spring Boot 4 (`src/main/resources/application.properties`) with env-based config and Flyway migrations.
- Frontend: Vite app in `ui/`; dev proxy to backend on `/admin/*` and `/webhooks`.
- Production UI intent exists in docs: built UI should be served by Spring under `/admin-ui/*`.
- No `Dockerfile` exists yet.
- No Spring SPA fallback controller/config exists yet for `/admin-ui/*` deep links.
- Existing webhook helper scripts:
  - `scripts/run-app.sh` handles local tunnel URL sync.
  - `scripts/fub-webhook-reactivate.sh` reactivates disabled hooks.

## Constraints
- Keep API routes untouched: `/admin/*`, `/webhooks/*`, `/health`.
- Hosted webhook URL updates must be deployment/runtime operations, not Docker image build concerns.
- Start with single-replica deployment for current SSE model.

## Decision
Implement a platform-agnostic Docker readiness slice:
1. multi-stage Docker build for backend + UI
2. Spring `/admin-ui/*` SPA fallback
3. hosted webhook URL sync script using `PUBLIC_BASE_URL`
4. docs + tests for routing/script contracts
