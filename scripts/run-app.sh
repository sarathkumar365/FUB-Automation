#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-}"
shift || true

PORT=8080
UI_PORT=5173
DEV_PROFILE="local"
PROD_PROFILE="prod"
TUNNEL_LOG="$(mktemp -t cloudflared-log.XXXXXX)"
CLOUDFLARED_PID=""
APP_LOG_DIR="logs"
APP_LOG_FILE="${APP_LOG_DIR}/backend.log"
UI_LOG_FILE="${APP_LOG_DIR}/frontend.log"
STARTUP_LOG_FILE="${APP_LOG_DIR}/startup.log"
FUB_WEBHOOK_EVENTS_FILE="${ROOT_DIR}/config/fub-webhook-events.txt"
COLOR_RESET=""
COLOR_INFO=""
COLOR_WARN=""
COLOR_ERROR=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-app.sh dev [--port <port>] [--profile <spring_profile>]
  ./scripts/run-app.sh prod [--port <port>] [--profile <spring_profile>]

Examples:
  ./scripts/run-app.sh dev
  ./scripts/run-app.sh dev --port 8080
  ./scripts/run-app.sh prod --profile prod
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "Missing required command: $1"
    exit 1
  fi
}

init_startup_log() {
  mkdir -p "${APP_LOG_DIR}"
  : > "${STARTUP_LOG_FILE}"
}

init_colors() {
  if [[ -t 1 ]]; then
    COLOR_RESET="$(printf '\033[0m')"
    COLOR_INFO="$(printf '\033[32m')"
    COLOR_WARN="$(printf '\033[33m')"
    COLOR_ERROR="$(printf '\033[31m')"
  fi
}

log_info() {
  local message="$1"
  local timestamp
  local line
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  line="${timestamp} [INFO] ${message}"
  printf '%s\n' "${line}" >> "${STARTUP_LOG_FILE}"
  if [[ -n "${COLOR_INFO}" ]]; then
    printf '%b%s%b\n' "${COLOR_INFO}" "${line}" "${COLOR_RESET}"
  else
    printf '%s\n' "${line}"
  fi
}

log_warn() {
  local message="$1"
  local timestamp
  local line
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  line="${timestamp} [WARN] ${message}"
  printf '%s\n' "${line}" >> "${STARTUP_LOG_FILE}"
  if [[ -n "${COLOR_WARN}" ]]; then
    printf '%b%s%b\n' "${COLOR_WARN}" "${line}" "${COLOR_RESET}"
  else
    printf '%s\n' "${line}"
  fi
}

log_error() {
  local message="$1"
  local timestamp
  local line
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  line="${timestamp} [ERROR] ${message}"
  printf '%s\n' "${line}" >> "${STARTUP_LOG_FILE}"
  if [[ -n "${COLOR_ERROR}" ]]; then
    printf '%b%s%b\n' "${COLOR_ERROR}" "${line}" "${COLOR_RESET}" >&2
  else
    printf '%s\n' "${line}" >&2
  fi
}

load_env_file() {
  if [[ -f "$ROOT_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$ROOT_DIR/.env"
    set +a
  else
    log_warn "No .env file found at $ROOT_DIR/.env (continuing with existing environment)."
  fi
}

start_tunnel() {
  local port="$1"
  require_cmd cloudflared
  log_info "Starting cloudflared tunnel -> http://localhost:${port}"
  cloudflared tunnel --url "http://localhost:${port}" >"${TUNNEL_LOG}" 2>&1 &
  CLOUDFLARED_PID=$!
}

wait_for_tunnel_url() {
  local timeout_seconds=30
  local elapsed=0
  while [[ "${elapsed}" -lt "${timeout_seconds}" ]]; do
    if [[ -f "${TUNNEL_LOG}" ]]; then
      local url
      url="$(grep -Eo 'https://[a-zA-Z0-9.-]+\.trycloudflare\.com' "${TUNNEL_LOG}" | tail -n1 || true)"
      if [[ -n "${url}" ]]; then
        echo "${url}"
        return 0
      fi
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 1
}

wait_for_ui_url() {
  local url="$1"
  local timeout_seconds="$2"
  local elapsed=0

  while [[ "${elapsed}" -lt "${timeout_seconds}" ]]; do
    if curl -sS --fail "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  return 1
}

build_auth_header() {
  local encoded
  encoded="$(printf '%s:' "${FUB_API_KEY}" | base64 | tr -d '\n')"
  printf 'Authorization: Basic %s' "${encoded}"
}

load_managed_fub_events() {
  local events_file="${FUB_WEBHOOK_EVENTS_FILE}"
  if [[ ! -f "${events_file}" ]]; then
    return 0
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
  done < "${events_file}"

  if [[ "${#events[@]}" -eq 0 ]]; then
    return 0
  fi

  printf '%s\n' "${events[@]}"
}

create_fub_webhook() {
  local event_name="$1"
  local target_url="$2"
  local auth_header="$3"
  local response

  if ! response="$(curl -sS --fail -X POST "https://api.followupboss.com/v1/webhooks" \
    -H "${auth_header}" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}" \
    -H "Accept: application/json" \
    -H "Content-Type: application/json" \
    --data "{\"event\":\"${event_name}\",\"url\":\"${target_url}\"}")"; then
    return 1
  fi

  printf '%s\n' "${response}"
}

sync_fub_webhook_url() {
  local tunnel_url="$1"
  local target_url="${tunnel_url}/webhooks/fub"

  if [[ ! -f "${FUB_WEBHOOK_EVENTS_FILE}" ]]; then
    log_warn "Webhook events config missing at ${FUB_WEBHOOK_EVENTS_FILE}; skipping webhook sync."
    return 0
  fi

  local -a managed_events=()
  local loaded_event
  while IFS= read -r loaded_event; do
    managed_events+=("${loaded_event}")
  done < <(load_managed_fub_events)

  if [[ -z "${FUB_API_KEY:-}" || -z "${FUB_X_SYSTEM:-}" || -z "${FUB_X_SYSTEM_KEY:-}" ]]; then
    log_warn "FUB credentials not fully set; skipping webhook sync."
    return 0
  fi

  if [[ "${#managed_events[@]}" -eq 0 ]]; then
    log_warn "No valid events found in ${FUB_WEBHOOK_EVENTS_FILE}; skipping webhook sync."
    return 0
  fi

  require_cmd jq
  require_cmd curl

  local auth_header
  auth_header="$(build_auth_header)"
  local webhook_list
  if ! webhook_list="$(curl -sS --fail https://api.followupboss.com/v1/webhooks \
    -H "${auth_header}" \
    -H "X-System: ${FUB_X_SYSTEM}" \
    -H "X-System-Key: ${FUB_X_SYSTEM_KEY}")"; then
    log_warn "Failed to fetch FUB webhooks; skipping webhook sync."
    return 0
  fi

  local event_name webhook_id current_url created_webhook created_id created_event created_url
  for event_name in "${managed_events[@]}"; do
    webhook_id="$(echo "${webhook_list}" | jq -r --arg event "${event_name}" '
      if type == "array" then
        (map(select(.event == $event)) | first | .id)
      elif has("webhooks") then
        (.webhooks | map(select(.event == $event)) | first | .id)
      else
        empty
      end // empty
    ')"
    current_url="$(echo "${webhook_list}" | jq -r --arg event "${event_name}" '
      if type == "array" then
        (map(select(.event == $event)) | first | .url)
      elif has("webhooks") then
        (.webhooks | map(select(.event == $event)) | first | .url)
      else
        empty
      end // empty
    ')"

    if [[ -z "${webhook_id}" ]]; then
      if ! created_webhook="$(create_fub_webhook "${event_name}" "${target_url}" "${auth_header}")"; then
        log_warn "No ${event_name} webhook found and create failed; continuing."
        continue
      fi
      created_id="$(echo "${created_webhook}" | jq -r '
        if type == "object" and has("id") then
          .id
        elif has("webhook") and (.webhook | type == "object") and (.webhook | has("id")) then
          .webhook.id
        else
          empty
        end // empty
      ')"
      created_event="$(echo "${created_webhook}" | jq -r '
        if type == "object" and has("event") then
          .event
        elif has("webhook") and (.webhook | type == "object") and (.webhook | has("event")) then
          .webhook.event
        else
          empty
        end // empty
      ')"
      created_url="$(echo "${created_webhook}" | jq -r '
        if type == "object" and has("url") then
          .url
        elif has("webhook") and (.webhook | type == "object") and (.webhook | has("url")) then
          .webhook.url
        else
          empty
        end // empty
      ')"
      log_info "Created FUB webhook event=${created_event:-${event_name}} id=${created_id:-unknown} url=${created_url:-${target_url}}"
      continue
    fi

    if [[ "${current_url}" == "${target_url}" ]]; then
      log_info "FUB ${event_name} webhook URL already up to date: ${target_url}"
      continue
    fi

    if ! curl -sS --fail -X PUT "https://api.followupboss.com/v1/webhooks/${webhook_id}" \
      -H "${auth_header}" \
      -H "X-System: ${FUB_X_SYSTEM}" \
      -H "X-System-Key: ${FUB_X_SYSTEM_KEY}" \
      -H "Content-Type: application/json" \
      --data "{\"url\":\"${target_url}\"}" >/dev/null; then
      log_warn "Failed to update FUB ${event_name} webhook id=${webhook_id}; continuing."
      continue
    fi

    log_info "Updated FUB ${event_name} webhook URL -> ${target_url}"
  done
}

APP_PID=""
UI_PID=""
TAIL_PID=""
APP_TAIL_PID=""
UI_TAIL_PID=""
CLEANUP_DONE=0

stop_process() {
  local pid="$1"
  if [[ -z "${pid}" ]]; then
    return 0
  fi
  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    return 0
  fi
  kill "${pid}" >/dev/null 2>&1 || true
  wait "${pid}" >/dev/null 2>&1 || true
}

cleanup() {
  if [[ "${CLEANUP_DONE}" -eq 1 ]]; then
    return 0
  fi
  CLEANUP_DONE=1

  stop_process "${APP_TAIL_PID}"
  stop_process "${UI_TAIL_PID}"
  stop_process "${TAIL_PID}"
  stop_process "${UI_PID}"
  stop_process "${APP_PID}"
  if [[ -n "${CLOUDFLARED_PID}" ]]; then
    stop_process "${CLOUDFLARED_PID}"
  fi
  rm -f "${TUNNEL_LOG}"
}

handle_interrupt() {
  local signal_name="$1"
  log_info "Received ${signal_name}; stopping backend/frontend/tunnel processes."
  exit 130
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)
      PORT="${2:-}"
      shift 2
      ;;
    --profile)
      if [[ "$MODE" == "dev" ]]; then
        DEV_PROFILE="${2:-}"
      else
        PROD_PROFILE="${2:-}"
      fi
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$MODE" != "dev" && "$MODE" != "prod" ]]; then
  usage
  exit 1
fi

init_startup_log
init_colors
log_info "run-app.sh started mode=${MODE} port=${PORT}"
require_cmd "$ROOT_DIR/mvnw"
require_cmd curl
load_env_file
cd "$ROOT_DIR"

if [[ "$MODE" == "prod" ]]; then
  log_info "Starting app in PROD mode on port ${PORT} with profile '${PROD_PROFILE}'"
  SERVER_PORT="${PORT}" SPRING_PROFILES_ACTIVE="${PROD_PROFILE}" ./mvnw spring-boot:run
  exit 0
fi

log_info "Starting app in DEV mode on port ${PORT} with profile '${DEV_PROFILE}'"
: > "${APP_LOG_FILE}"
: > "${UI_LOG_FILE}"
log_info "Dev log file: ${APP_LOG_FILE} (cleared on startup)"
log_info "Frontend log file: ${UI_LOG_FILE} (cleared on startup)"
log_info "Startup log file: ${STARTUP_LOG_FILE} (cleared on startup)"
require_cmd npm
if [[ ! -f "${ROOT_DIR}/ui/package.json" ]]; then
  log_error "Missing UI workspace at ${ROOT_DIR}/ui/package.json"
  exit 1
fi
ACTIVE_DEV_PROFILES="local"
if [[ "${DEV_PROFILE}" != "local" ]]; then
  ACTIVE_DEV_PROFILES="${ACTIVE_DEV_PROFILES},${DEV_PROFILE}"
fi
SERVER_PORT="${PORT}" SPRING_PROFILES_ACTIVE="${ACTIVE_DEV_PROFILES}" SPRING_OUTPUT_ANSI_ENABLED=ALWAYS APP_LOG_FILE="${APP_LOG_FILE}" ./mvnw spring-boot:run &
APP_PID=$!
FORCE_COLOR=1 "$(command -v npm)" run dev --prefix "${ROOT_DIR}/ui" >"${UI_LOG_FILE}" 2>&1 &
UI_PID=$!

trap cleanup EXIT
trap 'handle_interrupt INT' INT
trap 'handle_interrupt TERM' TERM

sleep 2
if ! kill -0 "${APP_PID}" >/dev/null 2>&1; then
  log_error "App failed to start. Check logs above."
  exit 1
fi
if ! kill -0 "${UI_PID}" >/dev/null 2>&1; then
  log_error "Frontend dev server failed to start. Check logs at ${UI_LOG_FILE}."
  exit 1
fi

log_info "App started (pid=${APP_PID})."
log_info "Frontend started (pid=${UI_PID})."
if wait_for_ui_url "http://localhost:${UI_PORT}" 30; then
  log_info "Frontend ready at http://localhost:${UI_PORT}/admin-ui/webhooks"
else
  log_error "Frontend process started but UI readiness check failed. Check logs at ${UI_LOG_FILE}."
  exit 1
fi
start_tunnel "${PORT}"

if ! kill -0 "${CLOUDFLARED_PID}" >/dev/null 2>&1; then
  log_error "cloudflared failed to start. Check logs:"
  cat "${TUNNEL_LOG}" >&2
  exit 1
fi

TUNNEL_URL="$(wait_for_tunnel_url || true)"
if [[ -z "${TUNNEL_URL}" ]]; then
  log_error "Could not determine cloudflared public URL. Check logs:"
  cat "${TUNNEL_LOG}" >&2
  exit 1
fi

log_info "Cloudflare tunnel URL: ${TUNNEL_URL}"
sync_fub_webhook_url "${TUNNEL_URL}"

log_info "Streaming backend/frontend/cloudflared logs. Press Ctrl+C to stop."
tail -f "${APP_LOG_FILE}" | tee -a "${STARTUP_LOG_FILE}" &
APP_TAIL_PID=$!
tail -f "${UI_LOG_FILE}" | tee -a "${STARTUP_LOG_FILE}" &
UI_TAIL_PID=$!
tail -f "${TUNNEL_LOG}" | tee -a "${STARTUP_LOG_FILE}" &
TAIL_PID=$!
wait "${CLOUDFLARED_PID}"
