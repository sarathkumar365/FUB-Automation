# UI Style Guide v1 (Canonical)

## Purpose
This document is the canonical UI style reference for the admin streaming experience in `automation-engine` UI 0.1.

Use this together with:
- `docs/ui-figma-reference.md` (Figma node references)
- `ui/src/styles/tokens.css` (code-level design tokens)

## Canonical layout
- Shell: left rail + primary content workspace.
- Primary page composition:
  - Filter toolbar (top)
  - Live feed list/table (main)
  - Event detail inspector (right pane)

## Core UX decisions (locked)
- Streaming state:
  - Use a corner live indicator icon (pulse dot / heartbeat style), not a full “Live” action button.
  - Use pause as icon control.
- Stream actions:
  - Keep pause control.
  - Do not include a stream “clear” action.
- Density:
  - Prefer low-clutter spacing and clear grouping.
  - Avoid dense telemetry blocks unless specifically requested.
- Tick strip:
  - Show compact activity ticks under the live feed header.

## Filter model (locked for v1)
- Use structured controls instead of a large freeform query input by default.
- Filters should map to backend query params:
  - `source`
  - `status`
  - `eventType`
  - optional time window (`from`/`to`, presented as a UI time window selector)
- Keep explicit `Apply` and `Reset` actions.

## Data shown in list/detail (platform-aligned)
List view should prioritize backend fields from webhook feed DTO:
- `eventId`
- `source`
- `eventType`
- `status`
- `receivedAt`

Detail pane should prioritize:
- `id`
- `eventId`
- `source`
- `eventType`
- `status`
- `payloadHash`
- `payload`
- `receivedAt`

Processed-call views should prioritize:
- `callId`
- `status`
- `ruleApplied`
- `taskId`
- `failureReason`
- `retryCount`
- `updatedAt`

## Visual language
- Tone: internal operations console; clean, high-clarity, professional.
- Surface model: light UI, subtle borders, low-elevation cards/panels.
- Color model:
  - Neutral base for structure and text.
  - One primary brand accent.
  - Semantic status colors (`ok`, `warn`, `bad`).
- Typography:
  - UI text: `Manrope`-first stack.
  - Technical values/IDs/timestamps: monospace companion.

## Component behavior notes
- Status must use both color + label text (not color-only).
- Controls and rows must remain keyboard-accessible.
- Detail inspector actions should stay minimal and context-safe.

## Change policy
- Any major visual/layout change must:
  1. Add/adjust a Figma frame under the canonical file.
  2. Update this document if decision-level rules changed.
  3. Keep `ui/src/styles/tokens.css` aligned with approved tokens.
