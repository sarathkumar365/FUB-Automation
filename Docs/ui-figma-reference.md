# UI Figma Reference

## Canonical file
- File: `Untitled`
- File key: `svLM7vfwHvmdxjoNE1Sr3U`
- URL: https://www.figma.com/design/svLM7vfwHvmdxjoNE1Sr3U/Untitled

## Approved baseline
- Canonical baseline frame: **Option 1 — approved and locked**
- Node ID: `node-id=23-2`
- Intent: Option 1 shell (Rail + Panel + Content + Inspector) with cyan+teal primary styling.
- Status: Implemented. Shell migration completed 2026-03-19. Option 1 is the mandatory layout for all current and future stream UI work.

## Supporting explorations (historical — do not use as baseline)
- `node-id=20-2`: first brand-new stream ops canvas
- `node-id=21-2`: cleaner / less-crowded variant
- `node-id=22-2`: structured filters + heartbeat variant

## Usage rules for future design/code work
- Always start from the approved Option 1 baseline (`node-id=23-2`).
- If a newer baseline is approved:
  - update this file to point `Approved baseline` to the new node
  - move the old baseline node to `Supporting explorations` with a note
- Keep style and token alignment with:
  - `Docs/archive/ui-style-guide-v1.md`
  - `ui/src/styles/tokens.css`
