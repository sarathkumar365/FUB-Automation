# Dev Hosting Deploy Runbook

Step-by-step for deploying the Automation Engine to **Railway** as a single
service that serves both the Spring API and the bundled React SPA. Captures
the actual gotchas we hit during the first deploy so the next operator
(or you in three months) doesn't have to re-discover them.

> **Scope:** dev/showcase host, single admin operator. For multi-tenant or
> production-grade work, this runbook is a starting point, not a target.

## Why Railway

Picked over Render and Fly because:
- **Always-on** — no sleep tax. FUB webhook delivery is reliable.
- **Single-platform** — Postgres add-on lives in the same project.
- **Simplest Docker workflow** — Railway detects the multi-stage `Dockerfile`
  in repo root, builds, and runs.

Costs ~$5–8/month. Detailed comparison lives in
[`dev-phase-hosting-comparison.md`](./dev-phase-hosting-comparison.md).

## Architecture summary

- One Railway service runs the multi-stage Docker build (Node → Maven → JRE
  Alpine). Final image bundles the SPA at `src/main/resources/static/` so
  Spring serves both API and UI from the same origin. No CORS.
- One Railway Postgres add-on, in the same project, talked to over the
  internal IPv6 network.
- `SPRING_PROFILES_ACTIVE=prod` activates `application-prod.properties` which
  hardens body caps, disables `show-sql`, **and turns the legacy "call →
  task" automation off** (kill switch — see
  [`Docs/features/disable-hardcoded-task-creation/`](../../features/disable-hardcoded-task-creation/)).
- Auth: stateless JWT bearer per [`RD-004`](../../repo-decisions/RD-004-admin-auth-uses-jwt-bearer.md).

## Pre-flight (do these before clicking Deploy)

1. **Local tests pass:**
   ```bash
   ./mvnw clean test
   cd ui && npm run lint && npm run build && npm test
   ```
2. **`dev` is at the latest commit** and pushed:
   ```bash
   git checkout dev
   git status
   git log --oneline -3
   git push origin dev
   ```
3. **You have the prod FUB credentials.** They should be a different
   X-System than local dev — register a new system in the FUB dashboard
   if needed. The same X-System credentials must be set on Railway and in
   your local `.env.prod`; otherwise inbound webhook signatures will all
   401 (the deployed app verifies the HMAC against
   `FUB_X_SYSTEM_KEY`, the sync script registers webhooks under the
   matching X-System identity, and they have to agree).
4. **Local `.env.prod` exists** (gitignored) with prod-only overrides:
   ```
   PUBLIC_BASE_URL=https://<your-railway-host>      # filled in after step 7 below
   FUB_API_KEY=<prod api key>
   FUB_X_SYSTEM=<prod system name>
   FUB_X_SYSTEM_KEY=<prod system key>
   ```
   Used by `scripts/fub-webhook-sync.sh`. Other vars (`DB_*`, `JWT_*`)
   stay in `.env` for local dev — they're not needed for the sync script.

## Railway one-time setup

### 1. Create the service from GitHub
- Project → **+ New** → **GitHub Repo** → pick this repo.
- Branch: **`dev`** (don't tie a dev host to `main`).
- Railway detects the `Dockerfile` at repo root automatically. No build
  command override needed.

### 2. Add Postgres in the same project
- **+ New** → **Database** → **Add PostgreSQL**.
- Wait for it to provision (10–30 s). The default service name is
  **`Postgres`** — variable references below assume that name. If you
  rename it, change the prefix everywhere accordingly.

### 3. Set env vars on the service (not the database)

Service → **Variables** → add each. Generate **fresh** values for prod;
do NOT reuse anything from your local `.env`.

```
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=${{PORT}}

# JWT — admin auth (RD-004)
JWT_SECRET=<paste output of: openssl rand -base64 48>
JWT_ISSUER=automation-engine
JWT_EXPIRY=8h

# First-boot admin user — seeder consumes these once when app_user is empty
ADMIN_AUTH_USERNAME=<choose>
ADMIN_AUTH_PASSWORD=<strong, NOT "devpass">

# Database — discrete refs, NOT ${{Postgres.DATABASE_URL}} (see Failure 1)
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USER=${{Postgres.PGUSER}}
DB_PASS=${{Postgres.PGPASSWORD}}

# JVM IPv6 flags — Railway internal network is IPv6-only (see Failure 3)
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Stack=true -Djava.net.preferIPv6Addresses=true

# FUB
FUB_BASE_URL=https://api.followupboss.com/v1
FUB_API_KEY=<prod api key>
FUB_X_SYSTEM=<prod X-System name — must match .env.prod>
FUB_X_SYSTEM_KEY=<prod X-System key — must match .env.prod>
```

You don't need to set `TASK_CREATION_ENABLED` — `application-prod.properties`
already pins it to `false` and that's what we want until the workflow-engine
replacement lands.

### 4. Generate a public domain
Service → **Settings** → **Networking** → **Generate Domain**. You'll get
`https://<some-name>-production.up.railway.app`. **Copy this URL** — it's
your `PUBLIC_BASE_URL` for the sync script and your "Hosted dev URL" for FUB.

### 5. Configure healthcheck
Service → **Settings** → **Deploy** → **Healthcheck Path** → `/health`. Leave
the timeout default.

### 6. First deploy
Railway probably triggered a build when you connected the repo. If not,
**Deployments** → **Deploy**. Watch the build log:

- Stage 1 (`ui-build`): ~30–60 s — `npm ci` + `npm run build`.
- Stage 2 (`app-build`): ~2–5 min on first run (downloads all Maven deps).
- Stage 3 (`runtime`): few seconds, then container starts.

Look for these lines in the runtime log:
```
Picked up JAVA_TOOL_OPTIONS: -Djava.net.preferIPv6Stack=true -Djava.net.preferIPv6Addresses=true
Started AutomationEngineApplication in X.X seconds
Successfully validated 16 migrations
Schema "public" is up to date. No migration necessary.
AdminUserSeeder: inserted ADMIN user username=<your-admin>
```

If any of those are missing or replaced by errors, jump to **Failure modes**
below.

### 7. Update `PUBLIC_BASE_URL` in `.env.prod`
Replace the placeholder with the real Railway URL.

## Per-deploy steps (after every push to `dev` that should ship)

Railway redeploys automatically on push. You don't need to click anything.
Each deploy:

1. **Watch the build log** — same three stages. Failures usually surface
   in stage 2 (Maven build) or stage 3 (Spring boot).
2. **Smoke checks** from your laptop:
   ```bash
   HOST=https://<your-railway-host>

   curl -i $HOST/health                              # 200, "Automation Engine is running!"
   curl -i $HOST/admin-ui                            # 200, HTML body
   curl -i $HOST/admin-ui/leads/anything             # 200, HTML body (deep-link refresh)
   curl -i $HOST/admin/leads                         # 401, {"error":"unauthorized"}

   # Login
   TOKEN=$(curl -s -X POST -H 'Content-Type: application/json' \
     -d '{"username":"<your-admin>","password":"<your-prod-password>"}' \
     $HOST/admin/auth/login | jq -r .token)
   echo "${TOKEN:0:40}…"                             # JWT, not "null"

   curl -i -H "Authorization: Bearer $TOKEN" $HOST/admin/auth/me
   # 200, {"username":"<your-admin>","role":"ADMIN"}
   ```
3. **Open the SPA** at `$HOST/admin-ui/login` in a browser. Sign in.

## After first deploy: re-point FUB webhooks

Only on the first deploy or when the URL changes (custom domain, host
migration, project recreation). Code redeploys don't change the URL.

```bash
./scripts/fub-webhook-sync.sh --dry-run    # preview
./scripts/fub-webhook-sync.sh              # apply
```

The script reads `.env.prod` (overlay) on top of `.env` (defaults). For each
event in `config/fub-webhook-events.txt` (currently `callsCreated`,
`peopleCreated`, `peopleUpdated`) it creates or updates the FUB webhook
to point at `${PUBLIC_BASE_URL}/webhooks/fub`.

## Verify the kill switch is active

Once FUB is re-pointed, trigger an event (ring a tracked phone). Watch
Railway logs. You should see:

```
Webhook request received source=fub ...
Processing webhook event eventId=... source=fub normalizedDomain=CALL ...
Call moved to PROCESSING callId=...
Fetched call details from FUB callId=...
Skipping task creation due to action guard ... reason=TASK_CREATION_DISABLED
Call marked SKIPPED callId=... reason=TASK_CREATION_DISABLED
```

If you see "Calling FUB createTask" anywhere — the kill switch is **not**
fired. Check that `application-prod.properties` is on the classpath (it is,
once `SPRING_PROFILES_ACTIVE=prod` is set).

## Failure modes (the four we actually hit)

### Failure 1 — `Driver org.postgresql.Driver claims to not accept jdbcUrl, postgresql://...`

```
Caused by: ... Driver org.postgresql.Driver claims to not accept jdbcUrl,
postgresql://postgres:<password>@postgres.railway.internal:5432/railway
```

**Cause:** `DB_URL` is `${{Postgres.DATABASE_URL}}`. Railway's
`DATABASE_URL` is `postgresql://user:pass@host:port/db` — the Postgres JDBC
driver does NOT accept that scheme; it needs `jdbc:postgresql://` and
expects user/password as **separate** parameters.

**Fix:** use the discrete refs:
```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USER=${{Postgres.PGUSER}}
DB_PASS=${{Postgres.PGPASSWORD}}
```

### Failure 2 — `UnknownHostException: postgres:<password>@postgres.railway.internal`

```
Caused by: java.net.UnknownHostException: postgres:<password>@postgres.railway.internal
```

**Cause:** same as Failure 1 — the JDBC driver tried to use the malformed
URL and the entire `user:pass@host` got treated as a single hostname.

**Fix:** same as Failure 1.

### Failure 3 — `Unable to obtain connection from database: The connection attempt failed.` (SQL state `08001`)

After fixing the URL format, you may still see:
```
SQL State : 08001
Caused by: org.postgresql.util.PSQLException: The connection attempt failed.
```

**Cause:** Railway's internal network between services is **IPv6-only**.
JVMs default to preferring IPv4, so the JDBC driver can't reach
`postgres.railway.internal`.

**Fix:** add this env var on the service:
```
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Stack=true -Djava.net.preferIPv6Addresses=true
```

After save, the next deploy will log `Picked up JAVA_TOOL_OPTIONS: ...`
and Hikari will connect.

### Failure 4 — Every webhook returns 401 from the deployed app

**Cause:** the FUB X-System credentials registered by the sync script don't
match the credentials the deployed app uses to verify HMACs. Inbound
webhooks signed with one key fail signature verification against the
other.

**Fix:** make sure these are *identical* in three places:
- Railway service env vars: `FUB_X_SYSTEM`, `FUB_X_SYSTEM_KEY`, `FUB_API_KEY`.
- Local `.env.prod`: same three keys.
- FUB dashboard: the X-System under which the webhooks are actually
  registered.

The sync script registers webhooks under whichever X-System credentials
it sees in the env. The deployed app verifies signatures using whichever
X-System key Railway's env var holds. They must agree.

## Recovery / rollback

Railway's **Deployments** tab keeps every prior deploy. To roll back:
- Find the last good deployment.
- Click "..." → **Redeploy** on that one.

Database migrations are forward-only (Flyway). Rolling back the app does
NOT roll back the schema. If a migration is the cause of the failure, you
need a manual `DOWN` SQL or a forward fix-it migration.

## Operational scripts reference

### `scripts/fub-webhook-sync.sh`
Upserts FUB webhooks for managed events (in `config/fub-webhook-events.txt`)
to point at `${PUBLIC_BASE_URL}/webhooks/fub`. Idempotent. Run once per
URL change.

Env precedence (later wins):
1. shell environment
2. `.env`           (typically dev credentials)
3. `.env.prod`      (gitignored; if present, overrides `.env` so the script
                     targets prod by default)

```bash
./scripts/fub-webhook-sync.sh --dry-run
./scripts/fub-webhook-sync.sh
```

### `scripts/fub-webhook-reactivate.sh`
Re-enables FUB webhooks that FUB auto-disabled after repeated delivery
failures (e.g. while the deployed app was down). Doesn't change URLs.
Useful in dev when cloudflared tunnels died and FUB marked the webhooks
as broken.

### `scripts/run-app.sh`
Local dev convenience. **Not used on Railway.** Two modes:
- `./scripts/run-app.sh dev` — backend + Vite dev server + cloudflared
  quick tunnel.
- `./scripts/run-app.sh prod` — backend with `SPRING_PROFILES_ACTIVE=prod`
  for local prod-flavoured testing. Pair with a separate
  `cd ui && npm run dev` if you want the SPA.

## Reference

- Auth design: [`RD-004-admin-auth-uses-jwt-bearer.md`](../../repo-decisions/RD-004-admin-auth-uses-jwt-bearer.md)
- Security checklist: [`dev-hosting-security-checklist.md`](./dev-hosting-security-checklist.md)
- Bundled-SPA Dockerfile design: [`Docs/features/railway-deploy-bundled-spa/plan.md`](../../features/railway-deploy-bundled-spa/plan.md)
- Kill switch design: [`Docs/features/disable-hardcoded-task-creation/plan.md`](../../features/disable-hardcoded-task-creation/plan.md)
- Hosting comparison (Railway vs alternatives): [`dev-phase-hosting-comparison.md`](./dev-phase-hosting-comparison.md)
