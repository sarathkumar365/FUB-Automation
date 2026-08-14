#!/usr/bin/env bash
# Stop hook — guards against ending a turn with broken/deviating UI work.
#
# If the working tree has uncommitted changes under ui/, run the fast UI gates
# (eslint + token-discipline guard + typecheck). If any fail, emit a "block"
# decision so the agent must fix them before the turn can end. Full build/test
# stay in `npm run check`; this keeps the per-stop cost low (no vite build / vitest).
set -uo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$ROOT" || exit 0

# No-op unless ui/ has uncommitted changes.
git status --porcelain -- ui/ | grep -q . || exit 0

cd ui || exit 0

if out="$(npm run --silent lint 2>&1 && node scripts/check-hex.mjs 2>&1 && npx --no-install tsc -b --noEmit 2>&1)"; then
  exit 0
fi

printf 'UI gates failed — fix before ending the turn (run `cd ui && npm run check`).\n\n%s' \
  "$(printf '%s' "$out" | tail -n 25)" | jq -Rs '{decision:"block",reason:.}'
exit 0
