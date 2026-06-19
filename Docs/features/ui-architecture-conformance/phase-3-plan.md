# Phase 3 — Boundary enforcement (UAC-04) + finalize AGENTS (UAC-06)

Goal: make the layering invariants Phases 1–2 established **unbreakable in CI**, so they can't silently
regress. One small source refactor (AppRail) to make `shared/` a true leaf; otherwise no behavior change.
Per review-before-commit: implement → present → wait for OK.

## Locked decisions (2026-06-18)
- **Mechanism:** built-in `no-restricted-imports` (zero new deps; matches our aliased specifiers).
- **Contracts guard:** yes — `platform/contracts/` restricted to a zod-only leaf.
- **AppRail smell (UAC-13):** **fix in this phase** (not deferred) → make `shared/` a clean leaf and add a
  `shared`-is-leaf lint zone.

---

## Verification (the rules already hold — this is a guardrail, not a fix)
- `platform → @modules`: only `httpJsonClient.ts` + `sseWebhookStreamAdapter.ts` (the sanctioned auth-token import).
- `ports → adapters`: **none**.
- `platform/contracts/`: imports **only `zod`** (clean leaf).
So adding the rules should yield **0 new lint errors** on first run. If any fire, that's a real find to fix.

## Approach — built-in `no-restricted-imports`, flat-config zones (no new dependency)

`eslint-plugin-import`'s `no-restricted-paths` is purpose-built but needs the plugin **and** a TS-alias
resolver (`eslint-import-resolver-typescript`) + config. Because Phase 1 made every cross-layer import use an
**alias** (`@modules/…`, `@platform/…`), the built-in rule can match those specifier strings directly — no
resolver, no new deps. The auth exception is expressed with flat-config `ignores`.

**Decision to confirm:** built-in `no-restricted-imports` (recommended — zero deps) vs `eslint-plugin-import`
`no-restricted-paths` (purpose-built, +2 deps + resolver config). Plan below assumes built-in.

### Config shape (`ui/eslint.config.js`)
Extract reusable pattern objects, compose per-zone (flat config overrides the whole rule per file, so each
zone restates the patterns that apply to it):

```js
const NO_DEEP_RELATIVE = { group: ['../../../../**'], message: 'Use a path alias (@app/@platform/@modules/@shared).' }
const NO_MODULES = { group: ['@modules/**'], message: 'platform must not import from modules (RD-011). Auth token store in httpJsonClient/sseWebhookStreamAdapter is the sole exception.' }
const NO_ADAPTERS = { group: ['../adapters/**', '@platform/adapters/**'], message: 'ports must not import from adapters (RD-011). Import contract types from @platform/contracts.' }
```

Config objects (added after the existing global block):
1. **global** (existing, unchanged): `patterns: [NO_DEEP_RELATIVE]` — applies everywhere.
2. **`files: ['src/platform/**/*.{ts,tsx}']`, `ignores: [httpJsonClient.ts, sseWebhookStreamAdapter.ts]`** →
   `patterns: [NO_DEEP_RELATIVE, NO_MODULES]`. (The 2 ignored files fall back to the global depth-only rule —
   that's the auth carve-out.)
3. **`files: ['src/platform/ports/**/*.{ts,tsx}']`** → `patterns: [NO_DEEP_RELATIVE, NO_MODULES, NO_ADAPTERS]`.
   (Ports are platform, so they also get NO_MODULES; ports block is more specific so it restates all three.)
4. **`files: ['src/platform/contracts/**/*.{ts,tsx}']`** →
   `patterns: [NO_DEEP_RELATIVE, { group: ['@app/**','@modules/**','@shared/**','@platform/**'], message: 'contracts is a leaf — import only zod.' }]`. Keeps contracts from accreting dependencies. Passes today.
5. **`files: ['src/shared/**/*.{ts,tsx}']`** →
   `patterns: [NO_DEEP_RELATIVE, { group: ['@app/**','@modules/**','@platform/**'], message: 'shared is a leaf — must not import from app/modules/platform.' }]`. Passes **after** the AppRail fix (step 1). `@shared`
   is not banned (intra-shared is fine).

`container.ts` legitimately imports adapters (it wires them) — not a port, so unaffected. `modules → @app`
stays allowed (shell hooks). Cross-module imports stay allowed (not an RD-011 invariant; out of scope).

## Step 1 — Fix AppRail so `shared/` is a leaf (UAC-13)
`shared/ui/AppRail.tsx` is the **only** `shared → modules/app/platform` edge: it imports
`@modules/auth/ui/LogoutButton`. AppRail is rendered **only** by `app/AppShell.tsx`, which **already imports
`LogoutButton`**. So inject it via a prop (app layer composes; shared primitive stays generic):
- `AppRail.tsx`: add prop `logout?: ReactNode`; drop the `@modules/auth` import; render `{logout}` in the
  footer in place of `<LogoutButton variant="rail" />` (keep `ThemeToggle` — it's `shared`).
- `AppShell.tsx`: `<AppRail logout={<LogoutButton variant="rail" />} />` (LogoutButton already imported here).
- Behavior unchanged — same component renders in the same slot. No AppRail unit test exists; shell tests
  render `AppShell` and should stay green (identical output).

## Steps 2+ (each reviewable; `npm run check` green between)
2. Edit `ui/eslint.config.js`: add the const pattern objects + zones 2–5 above (incl. `shared`-is-leaf).
3. Run `npm run lint` — expect **0 errors** (invariants hold after step 1). If any fire, fix or document.
4. **Smoke-test each zone** (the review dinged the depth rule for matching nothing): temporarily add an
   illegal import (platform → `@modules/workflows/...`; a port → `../adapters/http/...`; a contracts file →
   `@shared/...`; a shared file → `@modules/...`), confirm `eslint` errors with the right message, then
   revert. Record the smoke evidence in the implementation log.
5. **Finalize AGENTS.md (UAC-06 carryover):** remove the Phase-3 "to-be-finalized" marker; state the boundary
   rules are now lint-enforced (platform∌modules + auth exception; ports∌adapters; contracts/shared leaves).
6. Docs: tick UAC-04 (+ UAC-06 final) and add **UAC-13** (AppRail/shared-leaf, done) in `tracker.md` /
   `README.md`; append to `implementation-log.md`.

No new test file (ESLint rules aren't unit-tested here; step 4's smoke test is the done-signal). The AppRail
change is covered by existing shell tests. `npm run check` must stay green.

## Risks
- **Flat-config rule override** — a per-zone `no-restricted-imports` replaces (doesn't merge) the global one
  for matched files, so each zone restates `NO_DEEP_RELATIVE`. Mitigated by the shared const.
- **Glob/`ignores` correctness** — verify the auth carve-out works (those 2 files may import `@modules/auth`)
  and that ports glob is a subset handled by step 3's smoke test.
- **`**` matching** — patterns use `@modules/**` (not `@modules/*`) so nested paths match (minimatch `*`
  doesn't cross `/`).

## Acceptance
- AppRail no longer imports `@modules`; `grep` shows **zero** `shared → @app/@modules/@platform` edges.
- `npm run lint` green with all 5 zones in place; `npm run check` green.
- Smoke test shows each zone errors on a deliberate violation (evidence in the log).
- AGENTS.md no longer says "finalized in Phase 3"; boundary rules documented as enforced.
- UAC-04, UAC-06 (final), UAC-13 ticked. Tracker/README updated.

## Non-goals
- Banning cross-module imports (not an RD-011 invariant; separate policy if ever wanted).
- Adding `eslint-plugin-import`/boundaries plugin (built-in suffices for our aliased imports).
- Relocating other shell chrome (AppPanel/PanelNav/InspectorPanel stay in `shared/ui`; they don't import modules).
