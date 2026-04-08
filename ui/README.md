# Automation Engine — Admin UI

React + TypeScript + Vite frontend for the Automation Engine admin dashboard.

## What this is

This is the internal admin UI for the automation engine. It provides:
- **Live webhook feed** — SSE-streamed view of incoming webhook events in real time
- **Processed calls** — list of call outcomes with status, rule applied, and replay capability
- **Policy control plane** — create, update, and activate automation policies
- **Policy execution monitor** — view SLA enforcement runs and step-by-step outcomes

This is a standalone Vite app. In dev it proxies API calls to the Spring backend. In prod the built assets are served by Spring under `/admin-ui/*`.

## Running locally

Start the full dev stack (backend + frontend + Cloudflare tunnel + webhook sync):
```bash
./scripts/run-app.sh dev
```

Or run the frontend alone (requires backend already running on `:8080`):
```bash
cd ui
npm install
npm run dev
```

Vite dev server runs on `http://localhost:5173`. API calls are proxied to `http://localhost:8080`.

## Module boundaries

```
ui/src/
├── app/           ← router, providers, layout shell
├── platform/      ← API client, query setup, error mapping, SSE hooks
├── modules/
│   ├── webhooks/          ← webhook list, live feed, detail
│   ├── processed-calls/   ← processed calls list, replay
│   ├── policies/          ← policy CRUD and activation
│   └── policy-executions/ ← execution run list and detail
└── shared/        ← reusable types, utilities, primitives
```

## Tech stack

- React 19 + TypeScript + Vite
- Tailwind CSS + shadcn/ui
- React Router
- TanStack Query (server state)
- Zod (API boundary validation)
- Native `EventSource` for SSE
- Vitest + Testing Library (unit/component tests)
- Playwright (smoke/e2e tests)

## Building for production

```bash
npm run build
```

Built assets go to `ui/dist/`. The Spring Boot build picks these up and serves them under `/admin-ui/*`.

## Further reading

- Architecture and delivery rules: `ui/AGENTS.md`
- Implementation plan and phase status: `ui/Docs/ui-0.1-plan.md`
- Style guide: `Docs/archive/ui-style-guide-v1.md`
- Backend API reference: `Docs/deep-dive/10-flow-admin-apis.md`
