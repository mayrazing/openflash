import { request } from '../api/request.js'

const CODEX_PATH = '/api/admin/codex'
const RUNTIME_STATES = new Set([
  'AVAILABLE', 'NOT_INSTALLED', 'NOT_LOGGED_IN', 'DISABLED', 'ERROR',
])
const LOGIN_STATES = new Set([
  'IDLE', 'STARTING', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELED',
])

function safeVerificationUrl(value) {
  if (typeof value !== 'string' || !/^https:\/\/[^/]/.test(value)) return ''
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && url.host ? value : ''
  } catch {
    return ''
  }
}

export function normalizeLoginSnapshot(value) {
  const state = LOGIN_STATES.has(value?.state) ? value.state : 'FAILED'
  return {
    state,
    verificationUrl: state === 'PENDING' ? safeVerificationUrl(value?.verificationUrl) : '',
    userCode: state === 'PENDING' && typeof value?.userCode === 'string'
      ? value.userCode
      : '',
  }
}

export function normalizeCodexSnapshot(value) {
  const delay = value?.globalChangeMaxDelaySeconds
  return {
    enabled: value?.enabled === true,
    runtimeStatus: RUNTIME_STATES.has(value?.runtimeStatus) ? value.runtimeStatus : 'ERROR',
    login: normalizeLoginSnapshot(value?.login),
    globalChangeMaxDelaySeconds: Number.isInteger(delay) && delay >= 0 && delay <= 60
      ? delay
      : 60,
  }
}

export async function getCodexSnapshot() {
  return normalizeCodexSnapshot(await request(CODEX_PATH))
}

export function setCodexEnabled(enabled) {
  return request(`${CODEX_PATH}/enabled`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

export async function startCodexLogin() {
  return normalizeLoginSnapshot(await request(`${CODEX_PATH}/login`, { method: 'POST' }))
}

export async function cancelCodexLogin() {
  return normalizeLoginSnapshot(await request(`${CODEX_PATH}/login`, { method: 'DELETE' }))
}

export function logoutCodexAccount() {
  return request(`${CODEX_PATH}/account`, { method: 'DELETE' })
}
