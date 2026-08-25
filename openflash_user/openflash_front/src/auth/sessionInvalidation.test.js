import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { request, UnauthorizedError } from '../db/database.js'
import {
  beginAuthAttempt,
  captureAuthWindowToken,
  getActiveSessionInvalidation,
  isAuthWindowTokenCurrent,
  publishSessionInvalidation,
  subscribeSessionInvalidation,
} from './sessionInvalidation.js'

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
  beginAuthAttempt()
})

test('runtime invalidation codes publish one normalized reason through the shared channel', async () => {
  const cases = [
    { code: 40102, reason: 'SESSION_EXPIRED', reasonKey: 'errors.40102' },
    { code: 40103, reason: 'BANNED', reasonKey: 'errors.40103' },
    { code: 40104, reason: 'DELETED', reasonKey: 'errors.40104' },
  ]

  for (const expected of cases) {
    beginAuthAttempt()
    const received = []
    const unsubscribe = subscribeSessionInvalidation(event => received.push(event))
    globalThis.fetch = async () => response(401, { code: expected.code })

    await assert.rejects(request('/api/runtime-work'), error => {
      assert.equal(error instanceof UnauthorizedError, true)
      assert.equal(error.code, expected.code)
      return true
    })

    assert.deepEqual(received, [expected])
    unsubscribe()
  }
})

test('duplicate SSE and HTTP invalidations keep the first explicit reason and notify once', async () => {
  const received = []
  const unsubscribe = subscribeSessionInvalidation(event => received.push(event))

  assert.equal(publishSessionInvalidation({ reason: 'BANNED', code: 40103 }), true)
  globalThis.fetch = async () => response(401, { code: 40102 })
  await assert.rejects(request('/api/runtime-work'), UnauthorizedError)
  assert.equal(publishSessionInvalidation({ reason: 'DELETED', code: 40104 }), false)

  assert.deepEqual(received, [{
    code: 40103,
    reason: 'BANNED',
    reasonKey: 'errors.40103',
  }])
  unsubscribe()
})

test('ordinary unauthenticated response does not create an account invalidation transition', async () => {
  const received = []
  const unsubscribe = subscribeSessionInvalidation(event => received.push(event))
  globalThis.fetch = async () => response(401, { code: 40101 })

  await assert.rejects(request('/api/auth/me'), UnauthorizedError)

  assert.deepEqual(received, [])
  unsubscribe()
})

test('authentication continuations can detect an invalidation that won the request race', () => {
  publishSessionInvalidation({ reason: 'BANNED', code: 40103 })

  assert.deepEqual(getActiveSessionInvalidation(), {
    code: 40103,
    reason: 'BANNED',
    reasonKey: 'errors.40103',
  })

  beginAuthAttempt()
  assert.equal(getActiveSessionInvalidation(), null)
})

test('invalidation and each new auth attempt advance the auth window token', () => {
  const initialToken = captureAuthWindowToken()

  publishSessionInvalidation({ reason: 'SESSION_EXPIRED', code: 40102 })
  const invalidatedToken = captureAuthWindowToken()
  const nextAttemptToken = beginAuthAttempt()

  assert.notEqual(invalidatedToken, initialToken)
  assert.notEqual(nextAttemptToken, invalidatedToken)
  assert.equal(isAuthWindowTokenCurrent(initialToken), false)
  assert.equal(isAuthWindowTokenCurrent(invalidatedToken), false)
  assert.equal(isAuthWindowTokenCurrent(nextAttemptToken), true)
})

test('old pending settings cannot commit after invalidation opens a newer auth attempt', async () => {
  const oldAttemptToken = beginAuthAttempt()
  const pendingSettings = deferred()
  const state = {
    currentUser: { id: 7 },
    theme: 'dark',
    soundEnabled: true,
    language: 'de',
    preferences: { dailyGoal: 20 },
  }
  const unsubscribe = subscribeSessionInvalidation(() => {
    state.currentUser = null
    state.theme = 'system'
    state.soundEnabled = false
    state.language = 'fi'
    state.preferences = null
  })
  const continuation = pendingSettings.promise.then(settings => {
    if (!isAuthWindowTokenCurrent(oldAttemptToken)) return false
    state.currentUser = settings.currentUser
    state.theme = settings.theme
    state.soundEnabled = settings.soundEnabled
    state.language = settings.language
    state.preferences = settings.preferences
    return true
  })

  publishSessionInvalidation({ reason: 'SESSION_EXPIRED', code: 40102 })
  beginAuthAttempt()
  pendingSettings.resolve({
    currentUser: { id: 7 },
    theme: 'light',
    soundEnabled: true,
    language: 'en',
    preferences: { dailyGoal: 99 },
  })

  assert.equal(await continuation, false)
  assert.deepEqual(state, {
    currentUser: null,
    theme: 'system',
    soundEnabled: false,
    language: 'fi',
    preferences: null,
  })
  unsubscribe()
})

function deferred() {
  let resolve
  const promise = new Promise(resolvePromise => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function response(status, payload) {
  return {
    ok: false,
    status,
    async text() {
      return JSON.stringify(payload)
    },
  }
}
