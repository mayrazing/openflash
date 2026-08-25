import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { clearLocalAccountSession } from './db/database.js'
import {
  beginAuthAttempt,
  publishSessionInvalidation,
  subscribeSessionInvalidation,
} from './auth/sessionInvalidation.js'

const appSource = readFileSync(new URL('./App.jsx', import.meta.url), 'utf8')
const originalLocalStorage = globalThis.localStorage
const originalSessionStorage = globalThis.sessionStorage

afterEach(() => {
  globalThis.localStorage = originalLocalStorage
  globalThis.sessionStorage = originalSessionStorage
  beginAuthAttempt()
})

test('one App-style subscription performs one anonymous transition and cleanup for duplicate sources', () => {
  const localStorage = memoryStorage({ 'deck:7:card-sort-order': 'created_asc' })
  const sessionStorage = memoryStorage({ practice: 'stale' })
  globalThis.localStorage = localStorage
  globalThis.sessionStorage = sessionStorage
  let authStatus = 'authenticated'
  let currentUser = { id: 7 }
  let reasonKey = null
  let cleanupCount = 0
  const unsubscribe = subscribeSessionInvalidation(invalidation => {
    cleanupCount++
    clearLocalAccountSession(invalidation.reasonKey)
    currentUser = null
    authStatus = 'anonymous'
    reasonKey = invalidation.reasonKey
  })

  publishSessionInvalidation({ reason: 'BANNED', code: 40103 })
  publishSessionInvalidation({ reason: 'SESSION_EXPIRED', code: 40102 })

  assert.equal(cleanupCount, 1)
  assert.equal(authStatus, 'anonymous')
  assert.equal(currentUser, null)
  assert.equal(reasonKey, 'errors.40103')
  assert.deepEqual(localStorage.entries(), {})
  assert.deepEqual(sessionStorage.entries(), {
    'openflash.sessionInvalidationReason': 'errors.40103',
  })
  unsubscribe()
})

test('App wires one shared invalidation subscription and passes its reason to Auth', () => {
  assert.equal((appSource.match(/subscribeSessionInvalidation\(/g) ?? []).length, 1)
  assert.match(appSource, /clearLocalAccountSession\(invalidation\.reasonKey\)/)
  assert.match(appSource, /setCurrentUser\(null\)/)
  assert.match(appSource, /setAuthStatus\('anonymous'\)/)
  assert.match(appSource, /setSessionInvalidationReasonKey\(invalidation\.reasonKey\)/)
  assert.match(appSource, /<Auth[\s\S]*?sessionInvalidationReasonKey=/)
})

test('App captures and checks auth window tokens around bootstrap and authenticated settings', () => {
  assert.match(
    appSource,
    /const authWindowToken = captureAuthWindowToken\(\)[\s\S]*?const user = await getCurrentUser\(\)[\s\S]*?!isAuthWindowTokenCurrent\(authWindowToken\)/,
  )
  assert.match(
    appSource,
    /async function handleAuthenticated\(user, authWindowToken\)[\s\S]*?!isAuthWindowTokenCurrent\(authWindowToken\)[\s\S]*?const settings = await getSettings\(\)[\s\S]*?!isAuthWindowTokenCurrent\(authWindowToken\)/,
  )
})

test('a newer auth window replaces the old reason in App state and storage while duplicates stay first-wins', () => {
  const localStorage = memoryStorage({ account: 'old' })
  const sessionStorage = memoryStorage()
  globalThis.localStorage = localStorage
  globalThis.sessionStorage = sessionStorage
  let reasonKey = null
  let cleanupCount = 0
  const unsubscribe = subscribeSessionInvalidation(invalidation => {
    cleanupCount++
    clearLocalAccountSession(invalidation.reasonKey)
    reasonKey = invalidation.reasonKey
  })

  publishSessionInvalidation({ reason: 'SESSION_EXPIRED', code: 40102 })
  beginAuthAttempt()
  publishSessionInvalidation({ reason: 'BANNED', code: 40103 })
  publishSessionInvalidation({ reason: 'DELETED', code: 40104 })

  assert.equal(cleanupCount, 2)
  assert.equal(reasonKey, 'errors.40103')
  assert.deepEqual(sessionStorage.entries(), {
    'openflash.sessionInvalidationReason': 'errors.40103',
  })
  unsubscribe()
})

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    clear() {
      values.clear()
    },
    getItem(key) {
      return values.get(key) ?? null
    },
    removeItem(key) {
      values.delete(key)
    },
    setItem(key, value) {
      values.set(key, String(value))
    },
    entries() {
      return Object.fromEntries(values)
    },
  }
}
