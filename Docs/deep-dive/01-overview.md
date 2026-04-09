# Lead Management Platform: Overview

## 1) What This App Is and Why It Exists

### 1.1 The problem

A real estate team uses **Follow Up Boss (FUB)** as their CRM. Leads come in, agents get assigned, calls happen. But things fall through the cracks:

- A call is missed or too short — nobody follows up.
- A lead is assigned to an agent, but the agent never contacts them — nobody notices until the lead goes cold.

These are the two real-world problems this app solves.

### 1.2 How it solves them

The **Automation Engine** sits between Follow Up Boss and the team's workflow. It listens for events from FUB via webhooks and takes automated action:

**Scenario 1 — Call Automation (live today):**
A call happens in FUB. FUB fires a `callsCreated` webhook to this app. The app fetches the call details, evaluates the outcome, and decides:
- **Missed call** (duration = 0) → creates a "Call back" task in FUB assigned to the same agent, due tomorrow.
- **Short call** (duration ≤ 30 seconds) → creates a "Follow up" task in FUB.
- **"No answer" outcome** → creates a "Call back" task.
- **Connected call** (duration > 30 seconds) → no action needed, skip.

**Scenario 2 — Assignment SLA Enforcement (live today):**
A lead is created or updated in FUB. FUB fires a `peopleCreated` or `peopleUpdated` webhook. The app starts a policy-driven SLA timer:
1. Wait 5 minutes → check if the lead is still claimed by the assigned agent.
2. If claimed, wait 10 more minutes → check if the agent has made contact.
3. If no contact → trigger an action (reassign or move to pond). *Current behavior: action targets are validated from policy (`targetUserId` / `targetPondId`), action execution is log-only in the adapter for dev mode, and the step completes with `ACTION_SUCCESS`.*

### 1.3 Who uses it

- **The automation runs unattended** — webhooks arrive, processing happens automatically.
- **The Admin UI** (React app at `/admin-ui/`) gives the team visibility:
  - Live webhook feed (SSE streaming) — see events arrive in real time.
  - Processed calls list — see what happened to each call and why.
  - Replay failed calls — retry a failed call with one click.
  - Policy execution monitoring — see SLA enforcement runs and their step-by-step outcomes.
  - Policy control plane — create, update, activate policies.

### 1.4 Scope of this document

This document set covers the **backend implementation** in detail — every flow, every method, every configuration value. The frontend (React UI) is covered in a separate document.

**Branch snapshot:**
- `main` head: `fa27766`
- Current branch head: `83821d1`
- Delta: `131` files changed, `8664` insertions, `146` deletions

---

## 2) Local Development and Startup

### 2.1 Prerequisites

- Java 21
- Node.js 20+ and npm
- PostgreSQL (local or remote)
- Optional: `cloudflared` for temporary public demo links (required for FUB webhook delivery in dev)

### 2.2 Environment setup

```bash
cp .env.example .env
```

Required variables in `.env`:

| Variable | Purpose | Example |
|----------|---------|---------|
| `FUB_API_KEY` | FUB REST API key (Basic Auth) | `your-api-key` |
| `FUB_BASE_URL` | FUB API base URL | `https://api.followupboss.com/v1` |
| `FUB_X_SYSTEM` | X-System header (from FUB system registration) | `your-system-name` |
| `FUB_X_SYSTEM_KEY` | X-System-Key header (also used as webhook signing key) | `your-system-key` |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/automation_engine` |
| `DB_USER` | Database username | `postgres` |
| `DB_PASS` | Database password | `postgres` |
| `DEV_TEST_USER_ID` | In `local` profile, only process calls for this FUB user ID (safety guard) | `30` |

### 2.3 Running the app

**Option A: Backend only**
```bash
./mvnw spring-boot:run
```
Backend starts on `http://localhost:8080`. Flyway auto-applies migrations.

**Option B: Full dev stack** (backend + frontend + Cloudflare tunnel + webhook sync)
```bash
./scripts/run-app.sh dev
```

This is the main dev workflow. The script (`scripts/run-app.sh`, ~480 lines) orchestrates:

1. **Loads `.env`** — sources environment variables.
2. **Starts backend** — `./mvnw spring-boot:run` with `local` profile, logs to `logs/backend.log`.
3. **Starts frontend** — `npm run dev` in `ui/`, Vite dev server on `:5173`, logs to `logs/frontend.log`.
4. **Starts Cloudflare tunnel** — `cloudflared tunnel --url http://localhost:8080`, creates a temporary `*.trycloudflare.com` public URL pointing to the local backend.
5. **Syncs FUB webhooks** — reads `config/fub-webhook-events.txt` for the list of managed events (`callsCreated`, `peopleCreated`, `peopleUpdated`), then for each event:
   - Fetches existing FUB webhooks via `GET https://api.followupboss.com/v1/webhooks`
   - If no webhook exists for that event → creates one via `POST /v1/webhooks` with URL `{tunnel_url}/webhooks/fub`
   - If webhook exists but URL is stale → updates it via `PUT /v1/webhooks/{id}` with the new tunnel URL
   - If webhook exists and URL matches → skips (already up to date)
6. **Streams logs** — tails backend, frontend, and tunnel logs to the terminal with colorized output.
7. **Cleanup on Ctrl+C** — kills all child processes (backend, frontend, tunnel, log tailers).

**Why the tunnel and webhook sync exist:** FUB can only deliver webhooks to a public URL. Since local dev runs on `localhost`, the Cloudflare quick tunnel creates a temporary public URL. The tunnel URL changes every restart, so the script automatically updates the FUB webhook targets each time.

**Option C: Production mode**
```bash
./scripts/run-app.sh prod --profile prod
```
Runs backend only with the `prod` Spring profile. No tunnel, no frontend dev server, no webhook sync.

### 2.4 Managed webhook events

The file `config/fub-webhook-events.txt` defines which FUB webhook events the startup script manages:

```
callsCreated
peopleCreated
peopleUpdated
```

Adding a new event type to this file will cause the startup script to register a new webhook in FUB on next `dev` startup. The event must also be mapped in `StaticWebhookEventSupportResolver` to be processed (otherwise it will be persisted but not dispatched).

### 2.5 Log files

| File | Content |
|------|---------|
| `logs/backend.log` | Spring Boot backend output (cleared on each dev startup) |
| `logs/frontend.log` | Vite dev server output (cleared on each dev startup) |
| `logs/startup.log` | Startup orchestration log (cleared on each dev startup) |
