# Research

## Feature
`deploy-runbook`

## Goal
Capture the operational knowledge from the first Railway deploy in a single
runbook a future operator (or the same operator three months from now) can
follow without re-discovering the gotchas. Also fold in a small ergonomics
improvement to the FUB webhook sync script that came up during the same
deploy.

## What's already documented (and where it falls short)

- **README "Hosted dev environment"** — covers the env-var contract and the
  pre-deploy verification command. Doesn't mention Railway specifically, the
  sync script, or `.env.prod`.
- **`Docs/features/railway-deploy-bundled-spa/plan.md`** — design-level: why
  the SPA is bundled, what the Dockerfile does. One-line note about the sync
  script. Not an operational runbook.
- **`Docs/features/dev-hosting-security-hardening/`** — env-var contract,
  RD-004 reference, but no Railway-side wiring.
- **`Docs/hosting-decision/dev/dev-hosting-security-checklist.md`** — what
  was hardened and what's deferred. Not deploy steps.
- **`scripts/fub-webhook-sync.sh --help`** — accurate but inline. Not
  discoverable from markdown.

A fresh operator landing on the repo today would have to stitch all of
these together. They wouldn't know:

- Whether to use `${{Postgres.DATABASE_URL}}` or the discrete
  `${{Postgres.PGHOST}}` references (we hit this — the bundled URL has
  embedded `user:pass@` which the JDBC driver rejects).
- That Railway's internal network is IPv6-only and Java needs explicit
  flags (we hit this).
- How to point FUB webhooks at the new URL after deploy.
- That the FUB X-System credentials on Railway must match the credentials
  the sync script registers under (otherwise every webhook 401s on
  signature verification).

This feature consolidates that knowledge.

## Script change scope

While running through the deploy we noticed that the sync script reads
only `.env`, which typically holds dev credentials. Pointing it at prod
required exporting overrides on every invocation — easy to forget. Adding
a `.env.prod` overlay (sourced AFTER `.env` so its values win) lets the
script "just work" against prod without disturbing the dev `.env`.

The change is small (~10 lines in one bash function), backwards-compatible
(works fine without the override file), and tested by the existing
`FubWebhookSyncScriptTest` contract test.

## Repo decisions consulted

`RD-001`, `RD-002`, `RD-003`, `RD-004` — none constrain documentation
output or shell-script ergonomics. No conflict.

## Out of scope

- Production deployment (this is a dev-host runbook).
- Multi-region setup, custom domains beyond the Railway-issued one,
  blue/green deploys, rollbacks via tags. None of these apply to the dev
  envelope.
- Automating the FUB sync as part of deploy (would need a Railway
  post-deploy hook or a Spring `ApplicationRunner` — separate feature).
- Documenting the workflow engine's deploy story (orthogonal — it
  redeploys with the same artifact).
