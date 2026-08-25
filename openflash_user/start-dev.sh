#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${PROJECT_ROOT}/.." && pwd)"
COSYVOICE3_DIR="${PROJECT_ROOT}/cosyvoice3_tts_service"
COSYVOICE3_DATA_DIR_DEFAULT="${XDG_DATA_HOME:-${HOME}/.local/share}/openflash/cosyvoice3"
PIPER_DIR="${PROJECT_ROOT}/piper_tts_service"
PIPER_DATA_DIR_DEFAULT="${XDG_DATA_HOME:-${HOME}/.local/share}/openflash/piper"
BACKEND_DIR="${PROJECT_ROOT}/openflash_back"
FRONTEND_DIR="${PROJECT_ROOT}/openflash_front"
OPENFLASH_MDNS_HOST="openflash.local"

source "${REPOSITORY_ROOT}/scripts/dev-secrets.sh"
load_openflash_dev_secrets

cosyvoice3_pid=""
piper_pid=""
backend_pid=""
frontend_pid=""
mdns_pid=""

resolve_python() {
  if [[ -n "${COSYVOICE3_PYTHON:-}" ]]; then
    echo "${COSYVOICE3_PYTHON}"
    return
  fi
  if [[ -x "${COSYVOICE3_DATA_DIR:-${COSYVOICE3_DATA_DIR_DEFAULT}}/python/bin/python" ]]; then
    echo "${COSYVOICE3_DATA_DIR:-${COSYVOICE3_DATA_DIR_DEFAULT}}/python/bin/python"
    return
  fi
  if command -v python >/dev/null 2>&1; then
    command -v python
    return
  fi
  command -v python3
}

resolve_piper_python() {
  if [[ -n "${PIPER_PYTHON:-}" ]]; then
    echo "${PIPER_PYTHON}"
    return
  fi
  if [[ -x "${PIPER_DATA_DIR:-${PIPER_DATA_DIR_DEFAULT}}/python/bin/python" ]]; then
    echo "${PIPER_DATA_DIR:-${PIPER_DATA_DIR_DEFAULT}}/python/bin/python"
    return
  fi
  if [[ -x "${HOME}/miniconda3/envs/normal/bin/python" ]]; then
    echo "${HOME}/miniconda3/envs/normal/bin/python"
    return
  fi
  echo "Piper Python was not found. Set PIPER_PYTHON or prepare the normal environment." >&2
  return 1
}

resolve_maven() {
  if [[ -x "${BACKEND_DIR}/mvnw" ]]; then
    echo "${BACKEND_DIR}/mvnw"
    return
  fi
  if command -v mvn >/dev/null 2>&1; then
    command -v mvn
    return
  fi
  return 1
}

check_target_ports_available() {
  local port
  for port in "$@"; do
    if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
      echo "Error: port ${port} is already in use. Stop the existing service before running this launcher."
      return 1
    fi
  done
}

wait_for_http_service() {
  local status_url="$1"
  local pid="$2"
  local service_name="$3"
  local readiness_policy="$4"
  local http_status

  while true; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "${service_name} failed to start."
      return 1
    fi
    if ! http_status="$(
      curl --silent --output /dev/null \
        --connect-timeout 1 \
        --max-time 1 \
        --write-out '%{http_code}' \
        "${status_url}"
    )"; then
      http_status=""
    fi
    if [[ "${readiness_policy}" == "success" && "${http_status}" =~ ^2[0-9][0-9]$ ]]; then
      return
    fi
    if [[ "${readiness_policy}" == "response" && "${http_status}" =~ ^[1-5][0-9][0-9]$ ]]; then
      return
    fi
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "${service_name} failed to start."
      return 1
    fi
    sleep 1
  done
}

resolve_mdns_ipv4() {
  ip -4 route get 224.0.0.251 2>/dev/null \
    | awk 'NR == 1 { for (field = 1; field <= NF; field++) if ($field == "src") { print $(field + 1); exit } }'
}

publish_openflash_mdns() {
  local current_ip=""
  local published_ip=""
  local publisher_pid=""

  while true; do
    current_ip="$(resolve_mdns_ipv4 || true)"
    if [[ -z "${current_ip}" ]]; then
      if [[ -z "${published_ip}" ]]; then
        echo "Error: no LAN IPv4 address is available for ${OPENFLASH_MDNS_HOST}."
        return 1
      fi
    elif [[ "${current_ip}" != "${published_ip}" ]]; then
      if [[ -n "${publisher_pid}" ]] && kill -0 "${publisher_pid}" 2>/dev/null; then
        kill "${publisher_pid}" 2>/dev/null || true
        wait "${publisher_pid}" 2>/dev/null || true
      fi

      avahi-publish-address --no-reverse "${OPENFLASH_MDNS_HOST}" "${current_ip}" &
      publisher_pid=$!
      sleep 0.1
      if ! kill -0 "${publisher_pid}" 2>/dev/null; then
        wait "${publisher_pid}" 2>/dev/null || true
        echo "Error: failed to publish ${OPENFLASH_MDNS_HOST}."
        return 1
      fi
      published_ip="${current_ip}"
      echo "OpenFlash LAN URL: http://${OPENFLASH_MDNS_HOST}:5173"
      echo "OpenFlash LAN fallback: http://${published_ip}:5173"
    fi

    sleep 2
    if [[ -n "${publisher_pid}" ]] && ! kill -0 "${publisher_pid}" 2>/dev/null; then
      wait "${publisher_pid}" 2>/dev/null || true
      echo "Error: ${OPENFLASH_MDNS_HOST} publisher stopped."
      return 1
    fi
  done
}

kill_process_tree() {
  local pid="$1"
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return
  fi

  local child
  for child in $(pgrep -P "${pid}" 2>/dev/null || true); do
    kill_process_tree "${child}"
  done
  kill "${pid}" 2>/dev/null || true
}

# 只停止本脚本记录的子进程, 不影响独立启动的 AI runtime.
cleanup() {
  local exit_code="${1:-$?}"
  trap - EXIT INT TERM

  if [[ -n "${mdns_pid}" ]] && kill -0 "${mdns_pid}" 2>/dev/null; then
    kill_process_tree "${mdns_pid}"
  fi

  if [[ -n "${frontend_pid}" ]] && kill -0 "${frontend_pid}" 2>/dev/null; then
    kill_process_tree "${frontend_pid}"
  fi

  if [[ -n "${backend_pid}" ]] && kill -0 "${backend_pid}" 2>/dev/null; then
    kill_process_tree "${backend_pid}"
  fi

  if [[ -n "${piper_pid}" ]] && kill -0 "${piper_pid}" 2>/dev/null; then
    kill_process_tree "${piper_pid}"
  fi

  if [[ -n "${cosyvoice3_pid}" ]] && kill -0 "${cosyvoice3_pid}" 2>/dev/null; then
    kill_process_tree "${cosyvoice3_pid}"
  fi

  wait "${mdns_pid}" 2>/dev/null || true
  wait "${frontend_pid}" 2>/dev/null || true
  wait "${backend_pid}" 2>/dev/null || true
  wait "${piper_pid}" 2>/dev/null || true
  wait "${cosyvoice3_pid}" 2>/dev/null || true

  exit "${exit_code}"
}

trap cleanup EXIT
trap 'cleanup 130' INT
trap 'cleanup 143' TERM

for required_name in \
  OPENFLASH_ADMIN_INTERNAL_TOKEN \
  OPENFLASH_AI_RUNTIME_CORE_TOKEN \
  AI_ENCRYPTOR_PASSWORD \
  AI_ENCRYPTOR_SALT; do
  if [[ -z "${!required_name:-}" ]]; then
    echo "Error: ${required_name} is not set."
    exit 1
  fi
done

for required_command in ip awk avahi-publish-address; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    echo "Error: ${required_command} is required to publish ${OPENFLASH_MDNS_HOST}."
    exit 1
  fi
done

check_target_ports_available 8888 8889 8080 5173

cd "${COSYVOICE3_DIR}"
cosyvoice3_python="$(resolve_python)"
cosyvoice3_data_dir="${COSYVOICE3_DATA_DIR:-${COSYVOICE3_DATA_DIR_DEFAULT}}"
cosyvoice3_runtime_dir="${COSYVOICE3_RUNTIME_DIR:-${cosyvoice3_data_dir}/CosyVoice}"
cosyvoice3_model_dir="${COSYVOICE3_MODEL_DIR:-${cosyvoice3_runtime_dir}/pretrained_models/Fun-CosyVoice3-0.5B}"
cosyvoice3_prompt_audio="${COSYVOICE3_PROMPT_AUDIO:-${cosyvoice3_runtime_dir}/asset/english_reference_okay.wav}"
if ! "${cosyvoice3_python}" -c "import cmudict, fastapi, soundfile, torch, torchaudio, uvicorn; raise SystemExit(0 if torch.cuda.is_available() else 1)" >/dev/null 2>&1; then
  echo "CosyVoice3 CUDA dependencies are not ready for: ${cosyvoice3_python}"
  echo "Run: cd ${COSYVOICE3_DIR} && ${cosyvoice3_python} -m pip install -r requirements.txt"
  exit 1
fi
if [[ ! -f "${cosyvoice3_runtime_dir}/cosyvoice/cli/cosyvoice.py" ]] \
  || [[ ! -f "${cosyvoice3_model_dir}/llm.rl.pt" ]] \
  || [[ ! -f "${cosyvoice3_prompt_audio}" ]]; then
  echo "CosyVoice3 runtime, RL model, or prompt audio is missing."
  echo "Run: ${cosyvoice3_python} ${COSYVOICE3_DIR}/prepare_runtime.py"
  exit 1
fi
env \
  -u OPENFLASH_ADMIN_INTERNAL_TOKEN \
  -u OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
  -u OPENFLASH_AI_RUNTIME_CORE_TOKEN \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
  -u AI_ENCRYPTOR_PASSWORD \
  -u AI_ENCRYPTOR_SALT \
  "${cosyvoice3_python}" -W ignore::UserWarning -W ignore::FutureWarning \
    -m uvicorn app:app --host 127.0.0.1 --port 8888 &
cosyvoice3_pid=$!

echo "Waiting for CosyVoice3 TTS to be ready..."
if ! wait_for_http_service \
  "http://127.0.0.1:8888/health" \
  "${cosyvoice3_pid}" \
  "CosyVoice3 TTS" \
  success; then
  cleanup 1
fi
echo "CosyVoice3 TTS ready, starting Piper TTS..."

cd "${PIPER_DIR}"
if ! piper_python="$(resolve_piper_python)"; then
  exit 1
fi
piper_data_dir="${PIPER_DATA_DIR:-${PIPER_DATA_DIR_DEFAULT}}"
piper_model_path="${PIPER_MODEL_PATH:-${piper_data_dir}/en_US-libritts_r-medium.onnx}"
piper_config_path="${PIPER_CONFIG_PATH:-${piper_data_dir}/en_US-libritts_r-medium.onnx.json}"
if ! "${piper_python}" -c "import fastapi, onnxruntime, piper, uvicorn" >/dev/null 2>&1; then
  echo "Piper dependencies are not ready for: ${piper_python}"
  echo "Run: cd ${PIPER_DIR} && ${piper_python} -m pip install -r requirements.txt"
  exit 1
fi
if [[ ! -f "${piper_model_path}" ]] || [[ ! -f "${piper_config_path}" ]]; then
  echo "Piper voice model or config is missing."
  echo "Run: ${piper_python} ${PIPER_DIR}/prepare_runtime.py"
  exit 1
fi
env \
  -u OPENFLASH_ADMIN_INTERNAL_TOKEN \
  -u OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
  -u OPENFLASH_AI_RUNTIME_CORE_TOKEN \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
  -u AI_ENCRYPTOR_PASSWORD \
  -u AI_ENCRYPTOR_SALT \
  PIPER_DATA_DIR="${piper_data_dir}" \
  "${piper_python}" -m uvicorn app:app --host 127.0.0.1 --port 8889 &
piper_pid=$!

echo "Waiting for Piper TTS to be ready..."
if ! wait_for_http_service \
  "http://127.0.0.1:8889/health" \
  "${piper_pid}" \
  "Piper TTS" \
  success; then
  cleanup 1
fi
echo "Piper TTS ready, starting backend..."

cd "${BACKEND_DIR}"
if ! maven_cmd="$(resolve_maven)"; then
  echo "Maven not found. Install Maven or make ${BACKEND_DIR}/mvnw executable."
  exit 1
fi
env \
  -u OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
  OPENFLASH_AI_RUNTIME_BASE_URL=http://127.0.0.1:8082 \
  "${maven_cmd}" clean spring-boot:run &
backend_pid=$!

echo "Waiting for backend to be ready..."
if ! wait_for_http_service \
  "http://localhost:8080" \
  "${backend_pid}" \
  "Backend" \
  response; then
  cleanup 1
fi
echo "Backend ready, starting frontend..."

cd "${FRONTEND_DIR}"
env \
  -u OPENFLASH_ADMIN_INTERNAL_TOKEN \
  -u OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
  -u OPENFLASH_AI_RUNTIME_CORE_TOKEN \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
  -u OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT \
  -u AI_ENCRYPTOR_PASSWORD \
  -u AI_ENCRYPTOR_SALT \
  npm run dev -- --host &
frontend_pid=$!

publish_openflash_mdns &
mdns_pid=$!

wait -n "${cosyvoice3_pid}" "${piper_pid}" "${backend_pid}" "${frontend_pid}" "${mdns_pid}"
