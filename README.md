# Automation Engine

Automation Engine is a Follow Up Boss call automation project.

It ingests webhook events, evaluates call outcomes, and creates follow-up tasks automatically.  
This repository is built as a showcase of a production-style architecture (controller -> service -> port -> adapter -> repository), with a Spring Boot backend and a React admin UI.

## A Quick Story

1. A call happens, and the team wants to know what comes next.  
   The project opens with a simple operations view that sets the context for the workflow.

![Landing page](Docs/archive/images/landing%20page.png)

2. As webhooks arrive, the system captures each event and shows live activity so nothing feels like a black box.  
   You can watch what is coming in, in near real time.

![Live webhook ingestion](Docs/archive/images/live%20webhook%20ingestion.png)

3. Once calls are processed, the team gets a clear status view of outcomes and can replay failed ones quickly.  
   The goal is to close the loop from missed call to follow-up action.

![Processed calls](Docs/archive/images/processed%20call.png)

## What It Does Today (Scenario 1)

- Receives signed FUB webhooks (`/webhooks/fub`)
- Persists incoming webhook events (inbox-first pattern)
- Processes call events asynchronously
- Fetches call details from Follow Up Boss
- Applies outcome rules
- Creates follow-up tasks in Follow Up Boss
- Provides admin visibility through webhook history/detail/live feed
- Provides processed-calls visibility with replay for failed calls

## Stack

- Backend: Java 21, Spring Boot 4, Spring MVC, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React + TypeScript + Vite, TanStack Query, Zod, Tailwind
- Testing: JUnit + Spring tests, Vitest + Testing Library, Playwright smoke

## Repository Structure

- `src/main/java`: backend application code
- `src/main/resources`: app config, Flyway migrations, logging config
- `src/test/java`: backend unit/integration tests
- `ui/`: standalone frontend app
- `scripts/run-app.sh`: local dev/prod helper script (backend + frontend + optional tunnel flow)
- `docs/` and `Docs/`: design notes, architecture plans, and UI references

## Prerequisites

- Java 21
- Node.js 20+ and npm
- PostgreSQL (local or remote)
- Optional: `cloudflared` for temporary public demo links

## Local Setup

1. Create env file:

```bash
cp .env.example .env
```

2. Update required variables in `.env`:

- `DB_URL`, `DB_USER`, `DB_PASS`
- `FUB_BASE_URL`, `FUB_API_KEY`, `FUB_X_SYSTEM`, `FUB_X_SYSTEM_KEY`

3. Export env values:

```bash
set -a
source .env
set +a
```

## Running the Project

### Option A: Backend only

```bash
./mvnw spring-boot:run
```

### Option B: Backend + Frontend dev + Cloudflare quick tunnel

```bash
./scripts/run-app.sh dev
```

This starts:

- Backend on `http://localhost:8080`
- Frontend (Vite) on `http://localhost:5173/admin-ui/webhooks`
- Cloudflare quick tunnel for backend URL (`*.trycloudflare.com`)

### Option C: Prod profile backend launch helper (local prod-flavoured testing)

```bash
./scripts/run-app.sh prod
```

Sets `SPRING_PROFILES_ACTIVE=prod` so [`application-prod.properties`](src/main/resources/application-prod.properties) loads (10 MB body cap, `show-sql=false`, **task-creation kill switch on**). Useful for confirming the deployed config boots cleanly on your machine before pushing to Railway. Pair with `cd ui && npm run dev` in another terminal if you want the SPA reachable on `:5173`.

### Manual webhook reactivation (dev recovery)

If Follow Up Boss auto-disables managed dev webhooks after repeated delivery failures, run:

```bash
./scripts/fub-webhook-reactivate.sh
```

This command only re-enables disabled webhooks for managed events in `config/fub-webhook-events.txt`. It does not create webhooks or update webhook URLs.

## Frontend Commands

```bash
cd ui
npm install
npm run dev
npm run test
npm run lint
npm run build
```

## Test Commands

Backend:

```bash
./mvnw clean test
```

Frontend:

```bash
cd ui && npm test
```

Playwright smoke:

```bash
cd ui && npm run test:e2e
```

## Demo / Showcase Notes

- This project is currently optimized for local/dev showcase workflows.
- Admin UI is intended for internal visibility.
- Cloudflare quick tunnel URLs are temporary and change when restarted.
- For external demo viewers, keep your local app process running while sharing tunnel URLs.

## Hosted dev environment

The dev host runs on **Railway** as a single service that bundles the React SPA into the Spring Boot jar via a multi-stage `Dockerfile` at the repo root. One URL serves both API (`/admin/**`, `/webhooks/**`, `/health`) and SPA (`/`, `/admin-ui/**`). No CORS, no separate frontend deploy.

> **Step-by-step deploy procedure, including the gotchas we hit:** see [`Docs/hosting-decision/dev/deploy-runbook.md`](Docs/hosting-decision/dev/deploy-runbook.md). What follows below is the env-var contract; the runbook is the operational walkthrough.

### Env vars on the deployed service

| Env var | Required | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | Set to `prod` so [`application-prod.properties`](src/main/resources/application-prod.properties) loads — body-size cap, `show-sql=false`, **task-creation kill switch on** ([why](Docs/features/disable-hardcoded-task-creation/plan.md)). |
| `SERVER_PORT` | yes | On Railway: `${{PORT}}`. Spring binds to the dynamic port the platform assigns. |
| `JAVA_TOOL_OPTIONS` | yes (Railway) | `-Djava.net.preferIPv6Stack=true -Djava.net.preferIPv6Addresses=true`. Railway's internal network is IPv6-only; without these flags the JVM cannot reach the Postgres add-on. |
| `JWT_SECRET` | yes (non-`local`) | HS256 signing key; ≥ 32 chars. Generate with `openssl rand -base64 48`. Blank fails startup outside `local`. |
| `JWT_ISSUER` | optional | Default `automation-engine`. |
| `JWT_EXPIRY` | optional | Default `8h`. ISO-8601 duration. |
| `ADMIN_AUTH_USERNAME` | yes (first boot) | Used once to seed the initial ADMIN row in `app_user`. Subsequent rotations go through SQL or a future user-management UI. |
| `ADMIN_AUTH_PASSWORD` | yes (first boot) | BCrypt-hashed before insert. The seeder is one-shot — it never modifies an existing user. |
| `DB_URL` | yes | On Railway: `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`. **Do NOT use `${{Postgres.DATABASE_URL}}`** — that has `user:pass@` embedded which the JDBC driver rejects. |
| `DB_USER` | yes | On Railway: `${{Postgres.PGUSER}}`. |
| `DB_PASS` | yes | On Railway: `${{Postgres.PGPASSWORD}}`. |
| `FUB_BASE_URL` | yes | `https://api.followupboss.com/v1`. |
| `FUB_API_KEY` | yes | Prod FUB API key (typically a different X-System than your local dev). |
| `FUB_X_SYSTEM` | yes | Prod X-System name. **Must match the X-System the FUB webhooks are registered under** (see "After deploy" below). |
| `FUB_X_SYSTEM_KEY` | yes | Prod X-System key. **Must match the same X-System** — used to verify inbound webhook HMAC signatures. |

### Pre-deploy verification

```bash
# Confirm devtools is excluded from the packaged jar
./mvnw clean package -DskipTests
jar tf target/automation-engine-*.jar | grep -i devtools   # expect empty

# Generate a long-lived JWT secret
openssl rand -base64 48
```

### After deploy: re-point FUB webhooks at the new URL

Railway gives you a public URL (e.g. `https://your-app-production.up.railway.app`). FUB webhooks must be re-registered to point there. Use [`scripts/fub-webhook-sync.sh`](scripts/fub-webhook-sync.sh):

```bash
# One-time: create .env.prod (gitignored) with prod FUB credentials.
# The sync script reads it on top of .env so the prod X-System wins.
cat > .env.prod <<'EOF'
PUBLIC_BASE_URL=https://your-app-production.up.railway.app
FUB_API_KEY=<prod api key>
FUB_X_SYSTEM=<prod X-System name>
FUB_X_SYSTEM_KEY=<prod X-System key>
EOF

./scripts/fub-webhook-sync.sh --dry-run    # preview
./scripts/fub-webhook-sync.sh              # apply
```

The script upserts FUB webhooks for every event in [`config/fub-webhook-events.txt`](config/fub-webhook-events.txt) (`callsCreated`, `peopleCreated`, `peopleUpdated`).

### What the deployed instance does on real traffic

- Inbound `/webhooks/fub` → call data is saved to `processed_calls`.
- The legacy "create FUB task" automation is **off** in prod (kill switch). Calls land in `SKIPPED:TASK_CREATION_DISABLED`. The workflow-engine replacement is the next feature.
- Admin SPA at `/admin-ui/login` — JWT bearer auth per [`RD-004`](Docs/repo-decisions/RD-004-admin-auth-uses-jwt-bearer.md).

Security posture and known issues for the dev host: [`Docs/hosting-decision/dev/dev-hosting-security-checklist.md`](Docs/hosting-decision/dev/dev-hosting-security-checklist.md).

## Roadmap

- Scenario 2: intent/transcription-driven tasking
- UI production packaging and final hardening for hosted deployment
- Additional reliability and observability improvements
