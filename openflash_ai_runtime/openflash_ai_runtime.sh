#!/usr/bin/env bash

set -euo pipefail

RUNTIME_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${RUNTIME_ROOT}/.." && pwd)"

source "${PROJECT_ROOT}/scripts/dev-secrets.sh"
load_openflash_dev_secrets

for required_name in \
  OPENFLASH_AI_RUNTIME_ADMIN_TOKEN \
  OPENFLASH_AI_RUNTIME_CORE_TOKEN \
  OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD \
  OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT; do
  if [[ -z "${!required_name:-}" ]]; then
    echo "Error: ${required_name} is not set."
    exit 1
  fi
done

if [[ "${OPENFLASH_AI_RUNTIME_ADMIN_TOKEN}" == "${OPENFLASH_AI_RUNTIME_CORE_TOKEN}" ]]; then
  echo "Error: OPENFLASH_AI_RUNTIME_ADMIN_TOKEN and OPENFLASH_AI_RUNTIME_CORE_TOKEN must use different values."
  exit 1
fi

if (exec 3<>"/dev/tcp/127.0.0.1/8082") 2>/dev/null; then
  echo "Error: port 8082 is already in use. Stop the existing service before running this launcher."
  exit 1
fi

echo "Starting OpenFlash AI runtime: http://127.0.0.1:8082"
cd "${RUNTIME_ROOT}"
exec env \
  -u OPENFLASH_ADMIN_INTERNAL_TOKEN \
  -u AI_ENCRYPTOR_PASSWORD \
  -u AI_ENCRYPTOR_SALT \
  OPENFLASH_AI_RUNTIME_ADDRESS=127.0.0.1 \
  OPENFLASH_AI_RUNTIME_PORT=8082 \
  ./mvnw spring-boot:run
