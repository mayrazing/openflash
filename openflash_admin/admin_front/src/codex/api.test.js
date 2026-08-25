import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import {
  cancelCodexLogin,
  getCodexSnapshot,
  normalizeCodexSnapshot,
  normalizeLoginSnapshot,
  setCodexEnabled,
  startCodexLogin,
} from './api.js'
import * as codexApi from './api.js'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function successResponse(data = null) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ code: 200, data }),
  }
}

function captureFetch(data) {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return successResponse(data)
  }
  return calls
}

test('Codex API uses the exact snapshot and enabled contracts', async () => {
  const calls = captureFetch({
    enabled: true,
    runtimeStatus: 'AVAILABLE',
    login: { state: 'IDLE', verificationUrl: null, userCode: null },
    globalChangeMaxDelaySeconds: 60,
  })

  const snapshot = await getCodexSnapshot()
  await setCodexEnabled(false)

  assert.equal(calls[0].path, '/api/admin/codex')
  assert.equal(calls[0].options.method, undefined)
  assert.equal(calls[0].options.credentials, 'include')
  assert.equal(snapshot.runtimeStatus, 'AVAILABLE')
  assert.equal(calls[1].path, '/api/admin/codex/enabled')
  assert.equal(calls[1].options.method, 'PUT')
  assert.equal(calls[1].options.body, JSON.stringify({ enabled: false }))
  assert.deepEqual(Object.keys(JSON.parse(calls[1].options.body)), ['enabled'])
})

test('Codex login API uses POST to start and DELETE to cancel', async () => {
  const calls = captureFetch({ state: 'CANCELED', verificationUrl: null, userCode: null })

  await startCodexLogin()
  await cancelCodexLogin()

  assert.deepEqual(calls.map(call => [call.path, call.options.method]), [
    ['/api/admin/codex/login', 'POST'],
    ['/api/admin/codex/login', 'DELETE'],
  ])
})

test('Codex account logout uses DELETE on the fixed account path', async () => {
  const calls = captureFetch()

  assert.equal(typeof codexApi.logoutCodexAccount, 'function')
  await codexApi.logoutCodexAccount()

  assert.deepEqual(calls.map(call => [call.path, call.options.method]), [
    ['/api/admin/codex/account', 'DELETE'],
  ])
})

test('all documented runtime and login states survive normalization', () => {
  for (const runtimeStatus of [
    'AVAILABLE', 'NOT_INSTALLED', 'NOT_LOGGED_IN', 'DISABLED', 'ERROR',
  ]) {
    assert.equal(normalizeCodexSnapshot({ runtimeStatus }).runtimeStatus, runtimeStatus)
  }

  for (const state of [
    'IDLE', 'STARTING', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELED',
  ]) {
    assert.equal(normalizeLoginSnapshot({ state }).state, state)
  }
})

test('unknown states fail closed and malformed snapshot fields use safe values', () => {
  assert.deepEqual(normalizeCodexSnapshot({
    enabled: 'yes',
    runtimeStatus: 'FUTURE_RUNTIME',
    login: { state: 'FUTURE_LOGIN' },
    globalChangeMaxDelaySeconds: 900,
  }), {
    enabled: false,
    runtimeStatus: 'ERROR',
    login: { state: 'FAILED', verificationUrl: '', userCode: '' },
    globalChangeMaxDelaySeconds: 60,
  })
})

test('only PENDING preserves an https verification URL with a host and user code', () => {
  const valid = normalizeLoginSnapshot({
    state: 'PENDING',
    verificationUrl: 'https://auth.example/device?flow=1',
    userCode: 'ABCD-EFGH',
  })
  assert.equal(valid.verificationUrl, 'https://auth.example/device?flow=1')
  assert.equal(valid.userCode, 'ABCD-EFGH')

  for (const verificationUrl of [
    'http://auth.example/device',
    'javascript:alert(1)',
    'https:///missing-host',
    '/relative/device',
    '<img src=x onerror=alert(1)>',
  ]) {
    assert.equal(normalizeLoginSnapshot({
      state: 'PENDING',
      verificationUrl,
      userCode: 'SAFE-CODE',
    }).verificationUrl, '')
  }

  for (const state of ['IDLE', 'STARTING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELED']) {
    assert.deepEqual(normalizeLoginSnapshot({
      state,
      verificationUrl: 'https://auth.example/device',
      userCode: 'MUST-HIDE',
    }), { state, verificationUrl: '', userCode: '' })
  }
})
