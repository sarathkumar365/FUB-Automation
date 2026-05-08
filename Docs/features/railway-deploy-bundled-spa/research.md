# Research

## Feature
`railway-deploy-bundled-spa`

## Goal
Make the app deployable as **a single Railway service** by bundling the React SPA into the Spring Boot jar. The Spring server serves both the API (`/admin/**`, `/webhooks/**`, `/health`) and the SPA static assets (`/`, `/admin-ui/**`, `/assets/**`) from the same origin.

## Why bundle (vs split FE/BE)

- One service to deploy, one URL to give to FUB, one set of env vars.
- No CORS configuration needed — every browser request lands on the same origin.
- The SPA's `httpJsonClient` already uses relative paths (`/admin/...`), so no `VITE_API_BASE` plumbing is required.
- For a single-admin dev host, the cost of running two services and wiring CORS is not worth the architectural separation.

## Current state

- Frontend: built via `cd ui && npm run build` → outputs `ui/dist/` (Vite default, base `/`).
- Backend: standard Spring Boot 4 fat-jar via `./mvnw -DskipTests clean package`.
- No existing Dockerfile.
- No static resource serving from Spring today (`src/main/resources/static/` does not exist).
- React Router uses `BrowserRouter` (history API), so a deep link like `/admin-ui/webhooks` requires the server to return `index.html` for that path so the SPA can take over routing.

## Key constraint to handle: SPA fallback

When the user reloads `/admin-ui/leads/123`, the request hits Spring. Spring needs to:
1. Recognize this path doesn't map to a controller.
2. Forward to `/index.html` so the React app boots.
3. **Not** intercept actual API calls (`/admin/**`, `/webhooks/**`, `/health`) — those must reach their controllers.

Spring's static resource handling does part of the job (it serves `index.html`, `assets/*.js`, `assets/*.css` automatically when files exist under `src/main/resources/static/`). The missing piece is the fallback for client-side routes that don't correspond to actual files.

Solution: a single tiny `@Controller` that maps `GET /admin-ui` and `GET /admin-ui/**` to `forward:/index.html`. Three properties of this approach:
- It only matches the SPA's route prefix, so it cannot accidentally swallow API paths.
- It runs after specific controller mappings, so it never overrides them.
- It returns `forward:` (server-side dispatch) rather than a redirect, so the browser URL stays correct and React Router can read the path.

## Repo decisions consulted

- `RD-001`, `RD-002`, `RD-003` — none constrain packaging or static-resource serving.
- `RD-004` (admin auth = JWT bearer) — confirmed: no impact, since admin auth is on `/admin/**` API paths and the SPA serves from `/` + `/admin-ui/**` (different prefix). Spring Security's filter chain already lets the SPA static paths through via `anyRequest().permitAll()`.

## Out of scope (deliberate)

- Docker layer caching optimisation (single-stage build is fine for first deploy; multi-stage already trims runtime image).
- Asset compression / Brotli.
- Cache-Control header tuning beyond Spring's defaults.
- Bundle-size code-splitting (the Vite warning about >500 KB is pre-existing).
- A separate `phase-N-implementation.md` log — this is a single change set, not a multi-phase delivery.
