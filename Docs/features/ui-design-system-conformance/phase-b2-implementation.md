# Phase B2 — StatusScreen renderer (2026-06-04)

## Goal
The Direction-B body renderer that the three status pages pass content into: ghost-glyph watermark + centred
stack (pill eyebrow · title · body · helper · console strip · actions).

## What landed
- `app/status/StatusScreen.tsx` — `StatusScreen(content)` + the `StatusContent` shape (tone, glyph, copy,
  meta strip, optional helper/watermark/primary/secondary/extra).
- `src/test/status-screen.test.tsx` — 5 cases.

## Meaningful decisions
- **Tone = colour + a word.** `TONES` maps bad/warn/brand → `{ fg, bg }` token vars; the eyebrow always
  carries a word, never a bare colour (locked UX rule).
- **Secondary action is polymorphic:** `href` → a plain `<a>` (router-hook-free, so the error fallback can use
  it outside the router); `onClick` only → a `<button>` (e.g. `history.back()`). Primary is the brand `Button`.
- **Watermark is overridable** — pages pass the "404" numerals; otherwise it's the glyph at 372px / stroke 1.1.
- **Glyph sizing via `className`** (`h-[372px]`, `h-3.5`) relies on `cn`'s `twMerge` to override the icon
  wrappers' default `h-4 w-4` — verified `cn` uses tailwind-merge.
- `useRise` animates the foreground stack; the watermark stays put (`aria-hidden`).

## Validation
Test (5 cases) passes — copy/strip render, primary fires, secondary renders as link vs button, watermark
override. Full `npm run check` green — 390 tests (+5). Not browser-verified: nothing renders it until the
pages land (B3–B5).

## Repo decisions impact
**No new repo decision.** Presentational renderer; no Accepted decision touched. (Router-hook-free design is
what lets B3 reuse it from the top-level error boundary.)
