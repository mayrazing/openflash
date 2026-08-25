#!/usr/bin/env bash

set -euo pipefail

ADMIN_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${ADMIN_ROOT}/.." && pwd)"
BACKEND_DIR="${ADMIN_ROOT}/admin_back"
FRONTEND_DIR="${ADMIN_ROOT}/admin_front"

source "${PROJECT_ROOT}/scripts/dev-secrets.sh"
load_openflash_dev_secrets

backend_pid=""
frontend_pid=""

check_target_ports_available() {
  local port
  for port in "$@"; do
    if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
      echo "Error: port ${port} is already in use. Stop the existing service before running this launcher."
      return 1
    fi
  done
}

wait_for_service() {
  local status_url="$1"
  local pid="$2"
  local service_name="$3"
  local readiness_policy="${4:-success}"
  local http_status

  while true; do
    if ! http_status="$(
      curl --silent --output /dev/null \
        --connect-timeout 1 \
        --max-time 1 \
        --write-out '%{http_code}' \
        "${status_url}"
    )"; then
      http_status=""
    fi
    if [[ "${http_status}" =~ ^2[0-9][0-9]$ ]]; then
      return
    fi
    if [[ "${readiness_policy}" == "admin" && "${http_status}" == "401" ]]; then
      return
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "Error: ${service_name} failed to start. Later services were not started."
      return 1
    fi
    sleep 1
  done
}

stop_process_group() {
  local pid="$1"
  if [[ -z "${pid}" ]]; then
    return 0
  fi
  kill -TERM -- "-${pid}" 2>/dev/null || true
}

# 只停止本脚本记录的管理端前后端进程, 不影响独立启动的服务.
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM

  stop_process_group "${frontend_pid}"
  stop_process_group "${backend_pid}"
  wait "${frontend_pid}" 2>/dev/null || true
  wait "${backend_pid}" 2>/dev/null || true

  exit "${exit_code}"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for required_name in \
  OPENFLASH_ADMIN_INTERNAL_TOKEN \
  OPENFLASH_AI_RUNTIME_ADMIN_TOKEN; do
  if [[ -z "${!required_name:-}" ]]; then
    echo "Error: ${required_name} is not set."
    exit 1
  fi
done

check_target_ports_available 8081 5174

set -m
echo "Starting OpenFlash admin backend on port 8081..."
(
  cd "${BACKEND_DIR}"
  exec env \
    -u OPENFLASH_AI_RUNTIME_CORE_TOKEN \
    -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
    -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
    -u AI_ENCRYPTOR_PASSWORD \
    -u AI_ENCRYPTOR_SALT \
    OPENFLASH_ADMIN_PORT=8081 \
    OPENFLASH_AI_RUNTIME_BASE_URL=http://127.0.0.1:8082 \
    ./mvnw spring-boot:run
) &
backend_pid=$!

echo "Waiting for OpenFlash admin backend to be ready..."
wait_for_service \
  "http://127.0.0.1:8081/api/admin/auth/me" \
  "${backend_pid}" \
  "OpenFlash admin backend" \
  admin

echo "Starting OpenFlash admin frontend on port 5174..."
(
  cd "${FRONTEND_DIR}"
  exec env \
    -u OPENFLASH_ADMIN_INTERNAL_TOKEN \
    -u OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
    -u OPENFLASH_AI_RUNTIME_CORE_TOKEN \
    -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
    -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
    -u AI_ENCRYPTOR_PASSWORD \
    -u AI_ENCRYPTOR_SALT \
    npm run dev
) &
frontend_pid=$!
set +m

echo "OpenFlash admin: http://127.0.0.1:5174"
echo "Press Ctrl+C to stop both admin processes."

wait -n "${backend_pid}" "${frontend_pid}"
