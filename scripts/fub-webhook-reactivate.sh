#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
EVENTS_FILE="${ROOT_DIR}/config/fub-webhook-events.txt"
FUB_WEBHOOKS_ENDPOINT="https://api.followupboss.com/v1/webhooks"

log_info() {
  printf '%s\n' "[INFO] $1"
}

log_warn() {
  printf '%s\n' "[WARN] $1" >&2
}

log_error() {
  printf '%s\n' "[ERROR] $1" >&2
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Missing required command: $1"
    exit 1
  fi
}

load_env_file() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    log_warn "No .env file found at ${ENV_FILE}; using current environment values."
    return 0
  fi

  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
}

require_env_var() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    log_error "Missing required environment variable: ${name}"
    exit 1
  fi
}

build_auth_header() {
  local encoded
  encoded="$(printf '%s:' "${FUB_API_KEY}" | base64 | tr -d '\n')"
  printf 'authorization: Basic %s' "${encoded}"
}

load_managed_events() {
  if [[ ! -f "${EVENTS_FILE}" ]]; then
    log_error "Managed events file not found at ${EVENTS_FILE}"
    exit 1
  fi

  local -a events=()
  local line trimmed existing already_seen
  while IFS= read -r line || [[ -n "${line}" ]]; do
    trimmed="$(echo "${line}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
    if [[ -z "${trimmed}" || "${trimmed}" == \#* ]]; then
      continue
    fi

    already_seen=0
    for existing in "${events[@]:-}"; do
      if [[ "${existing}" == "${trimmed}" ]]; then
        already_seen=1
        break
      fi
    done
    if [[ "${already_seen}" -eq 1 ]]; then
      continue
    fi

    events+=("${trimmed}")
  done < "${EVENTS_FILE}"

  if [[ "${#events[@]}" -eq 0 ]]; then
    log_error "No valid events found in ${EVENTS_FILE}"
    exit 1
  fi

  printf '%s\n' "${events[@]}"
}

fetch_webhooks() {
  local auth_header="$1"

  curl -sS --fail "${FUB_WEBHOOKS_ENDPOINT}" \
    -H "accept: application/json" \
    -H "${auth_header}" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}"
}

activate_webhook() {
  local webhook_id="$1"
  local auth_header="$2"

  curl -sS --fail -X PUT "${FUB_WEBHOOKS_ENDPOINT}/${webhook_id}" \
    -H "accept: application/json" \
    -H "${auth_header}" \
    -H "content-type: application/json" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}" \
    --data '{"status":"Active"}' >/dev/null
}

main() {
  require_cmd curl
  require_cmd jq
  require_cmd base64

  load_env_file
  require_env_var "FUB_API_KEY"
  require_env_var "FUB_X_SYSTEM"
  require_env_var "FUB_X_SYSTEM_KEY"

  local -a managed_events=()
  local event_name
  while IFS= read -r event_name; do
    managed_events+=("${event_name}")
  done < <(load_managed_events)

  local auth_header
  auth_header="$(build_auth_header)"

  local webhook_response normalized_webhooks
  if ! webhook_response="$(fetch_webhooks "${auth_header}")"; then
    log_error "Failed to fetch Follow Up Boss webhooks."
    exit 1
  fi

  normalized_webhooks="$(echo "${webhook_response}" | jq -c '
    if type == "array" then .
    elif type == "object" and has("webhooks") and (.webhooks | type == "array") then .webhooks
    else []
    end
  ')"

  local checked=0
  local enabled=0
  local skipped=0
  local failed=0
  local event_checked event_enabled event_skipped event_failed
  local webhook_row webhook_id webhook_status
  local had_match

  for event_name in "${managed_events[@]}"; do
    event_checked=0
    event_enabled=0
    event_skipped=0
    event_failed=0
    had_match=0

    while IFS= read -r webhook_row; do
      if [[ -z "${webhook_row}" ]]; then
        continue
      fi

      had_match=1
      webhook_id="$(echo "${webhook_row}" | jq -r '.id // empty')"
      webhook_status="$(echo "${webhook_row}" | jq -r '.status // empty')"

      if [[ -z "${webhook_id}" ]]; then
        event_failed=$((event_failed + 1))
        failed=$((failed + 1))
        log_warn "event=${event_name} has webhook without id; skipping."
        continue
      fi

      event_checked=$((event_checked + 1))
      checked=$((checked + 1))

      if [[ "${webhook_status}" != "Disabled" ]]; then
        event_skipped=$((event_skipped + 1))
        skipped=$((skipped + 1))
        log_info "event=${event_name} id=${webhook_id} status=${webhook_status:-unknown} already non-disabled; skipped."
        continue
      fi

      if activate_webhook "${webhook_id}" "${auth_header}"; then
        event_enabled=$((event_enabled + 1))
        enabled=$((enabled + 1))
        log_info "event=${event_name} id=${webhook_id} status=Disabled -> Active."
      else
        event_failed=$((event_failed + 1))
        failed=$((failed + 1))
        log_warn "event=${event_name} id=${webhook_id} failed to activate."
      fi
    done < <(echo "${normalized_webhooks}" | jq -c --arg event "${event_name}" '.[] | select(.event == $event)')

    if [[ "${had_match}" -eq 0 ]]; then
      event_skipped=$((event_skipped + 1))
      skipped=$((skipped + 1))
      log_warn "event=${event_name} has no matching webhook in Follow Up Boss; skipped."
    fi

    log_info "event=${event_name} summary checked=${event_checked} enabled=${event_enabled} skipped=${event_skipped} failed=${event_failed}"
  done

  log_info "final summary checked=${checked} enabled=${enabled} skipped=${skipped} failed=${failed}"

  if [[ "${failed}" -gt 0 ]]; then
    exit 1
  fi
}

main "$@"
