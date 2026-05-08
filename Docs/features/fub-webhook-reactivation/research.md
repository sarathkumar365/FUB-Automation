# FUB Webhook Reactivation — Research

## Context
Development webhooks are registered against temporary Cloudflare tunnel URLs. When the local app is down or unreachable, Follow Up Boss may auto-disable those webhooks after repeated failures.

This creates a recurring local-dev bottleneck because webhook delivery stops until each disabled webhook is reactivated via API.

## Problem Statement
Current developer workflow handles URL sync via `scripts/run-app.sh`, but does not provide a dedicated manual recovery command that only re-enables disabled webhooks for managed events.

## Key Findings
- Follow Up Boss supports re-enabling with `PUT /v1/webhooks/{id}`.
- Activation requires:
  - webhook `id`
  - `Authorization: Basic ...`
  - `X-System`
  - `X-System-Key`
- Managed event scope already exists in `config/fub-webhook-events.txt`.

## Decision Alignment
- Repo-wide decisions remain in `Docs/repo-decisions/`.
- Feature delivery docs and phase tracking remain in `Docs/features/<feature-slug>/`.
- This feature is a narrow operational improvement for local development and does not change runtime automation logic.
