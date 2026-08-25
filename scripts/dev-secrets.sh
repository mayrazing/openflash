#!/usr/bin/env bash

# Loads stable local-development secrets, generating only values that are absent.
load_openflash_dev_secrets() {
  local state_root="${XDG_STATE_HOME:-${HOME:?HOME is required}/.local/state}"
  local secrets_file="${OPENFLASH_DEV_SECRETS_FILE:-${state_root}/openflash/dev-secrets.env}"
  local secrets_dir
  secrets_dir="$(dirname "${secrets_file}")"

  local -a secret_names=(
    OPENFLASH_ADMIN_INTERNAL_TOKEN
    OPENFLASH_AI_RUNTIME_ADMIN_TOKEN
    OPENFLASH_AI_RUNTIME_CORE_TOKEN
    OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD
    OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT
    AI_ENCRYPTOR_PASSWORD
    AI_ENCRYPTOR_SALT
  )
  local -A stored_values=()
  local line name value random_bytes
  local stored_new_value=false
  local previous_umask

  previous_umask="$(umask)"
  umask 077
  mkdir -p "${secrets_dir}"
  touch "${secrets_file}"
  chmod 600 "${secrets_file}"
  umask "${previous_umask}"

  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    if [[ "${line}" != *=* ]]; then
      echo "Error: malformed local development secrets file: ${secrets_file}" >&2
      return 1
    fi

    name="${line%%=*}"
    value="${line#*=}"
    case " ${secret_names[*]} " in
      *" ${name} "*) ;;
      *)
        echo "Error: unknown key ${name} in local development secrets file: ${secrets_file}" >&2
        return 1
        ;;
    esac
    if [[ -n "${stored_values[${name}]+present}" || -z "${value}" ]]; then
      echo "Error: invalid key ${name} in local development secrets file: ${secrets_file}" >&2
      return 1
    fi
    stored_values["${name}"]="${value}"
  done < "${secrets_file}"

  for name in "${secret_names[@]}"; do
    if [[ -z "${!name:-}" && -n "${stored_values[${name}]:-}" ]]; then
      printf -v "${name}" '%s' "${stored_values[${name}]}"
      export "${name}"
    fi

    if [[ -z "${!name:-}" ]]; then
      if [[ "${name}" == "AI_ENCRYPTOR_SALT" ]]; then
        # Preserve the previous local default so existing personal AI credentials remain decryptable.
        value="openflash-dev-salt"
      else
        if ! command -v openssl >/dev/null 2>&1; then
          echo "Error: openssl is required to generate local development secrets." >&2
          return 1
        fi
        random_bytes=32
        if [[ "${name}" == "OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT" ]]; then
          random_bytes=16
        fi
        value="$(openssl rand -base64 "${random_bytes}" | tr -d '\n')"
      fi
      printf -v "${name}" '%s' "${value}"
      export "${name}"
    fi

    if [[ -z "${stored_values[${name}]+present}" ]]; then
      value="${!name}"
      if [[ "${value}" == *$'\n'* || "${value}" == *$'\r'* ]]; then
        echo "Error: ${name} cannot contain a newline." >&2
        return 1
      fi
      printf '%s=%s\n' "${name}" "${value}" >> "${secrets_file}"
      stored_values["${name}"]="${value}"
      stored_new_value=true
    fi
  done

  if [[ "${stored_new_value}" == "true" ]]; then
    echo "Saved local development secrets to ${secrets_file}."
  fi
}
