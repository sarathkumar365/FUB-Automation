#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
EVENTS_FILE="${ROOT_DIR}/config/fub-webhook-events.txt"
FUB_WEBHOOKS_ENDPOINT="https://api.followupboss.com/v1/webhooks"
DRY_RUN=0

log_info() {
  printf '%s\n' "[INFO] $1"
}

log_warn() {
  printf '%s\n' "[WARN] $1" >&2
}

log_error() {
  printf '%s\n' "[ERROR] $1" >&2
}

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/fub-webhook-sync.sh [--dry-run]

Environment:
  PUBLIC_BASE_URL   Required hosted base URL (e.g., https://my-app.example.com)
  FUB_API_KEY       Required FUB API key
  FUB_X_SYSTEM      Required FUB X-System header value
  FUB_X_SYSTEM_KEY  Required FUB X-System-Key header value
USAGE
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

normalize_base_url() {
  local raw="${PUBLIC_BASE_URL}"
  raw="${raw%/}"
  printf '%s' "${raw}"
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

create_webhook() {
  local auth_header="$1"
  local event_name="$2"
  local target_url="$3"

  curl -sS --fail -X POST "${FUB_WEBHOOKS_ENDPOINT}" \
    -H "accept: application/json" \
    -H "${auth_header}" \
    -H "content-type: application/json" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}" \
    --data "{\"event\":\"${event_name}\",\"url\":\"${target_url}\"}" >/dev/null
}

update_webhook() {
  local auth_header="$1"
  local webhook_id="$2"
  local target_url="$3"

  curl -sS --fail -X PUT "${FUB_WEBHOOKS_ENDPOINT}/${webhook_id}" \
    -H "accept: application/json" \
    -H "${auth_header}" \
    -H "content-type: application/json" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}" \
    --data "{\"url\":\"${target_url}\",\"status\":\"Active\"}" >/dev/null
}

run_sync() {
  local auth_header="$1"
  local target_url="$2"
  local webhook_response normalized_webhooks

  webhook_response="$(fetch_webhooks "${auth_header}")"
  normalized_webhooks="$(echo "${webhook_response}" | jq -c '
    if type == "array" then .
    elif type == "object" and has("webhooks") and (.webhooks | type == "array") then .webhooks
    else []
    end
  ')"

  local -a managed_events=()
  local event_name
  while IFS= read -r event_name; do
    managed_events+=("${event_name}")
  done < <(load_managed_events)

  local matched_count create_count update_count skip_count
  matched_count=0
  create_count=0
  update_count=0
  skip_count=0

  local existing_count webhook_id current_url
  for event_name in "${managed_events[@]}"; do
    existing_count="$(echo "${normalized_webhooks}" | jq -r --arg event "${event_name}" '[ .[] | select(.event == $event) ] | length')"

    if [[ "${existing_count}" == "0" ]]; then
      create_count=$((create_count + 1))
      if [[ "${DRY_RUN}" -eq 1 ]]; then
        log_info "[dry-run] create event=${event_name} url=${target_url}"
      else
        create_webhook "${auth_header}" "${event_name}" "${target_url}"
        log_info "created event=${event_name} url=${target_url}"
      fi
      continue
    fi

    matched_count=$((matched_count + existing_count))

    while IFS='|' read -r webhook_id current_url; do
      if [[ "${current_url}" == "${target_url}" ]]; then
        skip_count=$((skip_count + 1))
        log_info "up-to-date event=${event_name} id=${webhook_id} url=${target_url}"
        continue
      fi

      update_count=$((update_count + 1))
      if [[ "${DRY_RUN}" -eq 1 ]]; then
        log_info "[dry-run] update event=${event_name} id=${webhook_id} from=${current_url} to=${target_url}"
      else
        update_webhook "${auth_header}" "${webhook_id}" "${target_url}"
        log_info "updated event=${event_name} id=${webhook_id} url=${target_url}"
      fi
    done < <(echo "${normalized_webhooks}" | jq -r --arg event "${event_name}" '.[] | select(.event == $event) | "\(.id // "")|\(.url // "")"')
  done

  log_info "summary matched=${matched_count} created=${create_count} updated=${update_count} skipped=${skip_count} dry_run=${DRY_RUN}"
}

main() {
  while (($#)); do
    case "$1" in
      --dry-run)
        DRY_RUN=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        log_error "Unknown argument: $1"
        usage
        exit 1
        ;;
    esac
  done

  require_cmd curl
  require_cmd jq
  require_cmd base64

  load_env_file

  require_env_var "PUBLIC_BASE_URL"
  require_env_var "FUB_API_KEY"
  require_env_var "FUB_X_SYSTEM"
  require_env_var "FUB_X_SYSTEM_KEY"

  local base_url target_url auth_header
  base_url="$(normalize_base_url)"
  target_url="${base_url}/webhooks/fub"
  auth_header="$(build_auth_header)"

  run_sync "${auth_header}" "${target_url}"
}

main "$@"
