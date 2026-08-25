import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./Auth.jsx', import.meta.url), 'utf8')
const locales = Object.fromEntries(['zh', 'en', 'fi', 'de'].map(language => [
  language,
  JSON.parse(readFileSync(new URL(`../locales/${language}.json`, import.meta.url), 'utf8')),
]))

test('auth mode tabs show semantic keyboard focus rings', () => {
  const focusClass = 'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-app-focus'

  assert.equal(source.split(focusClass).length - 1, 2)
})

test('auth displays the stored account invalidation reason as an accessible alert', () => {
  assert.match(source, /sessionInvalidationReasonKey/)
  assert.match(source, /role="alert"[\s\S]*?t\(sessionInvalidationReasonKey\)/)
})

test('all locales translate session expiry, banned, and deleted reasons', () => {
  for (const [language, locale] of Object.entries(locales)) {
    for (const code of ['40102', '40103', '40104']) {
      assert.equal(typeof locale.errors[code], 'string', `${language} errors.${code}`)
      assert.notEqual(locale.errors[code].trim(), '', `${language} errors.${code}`)
    }
  }
})

test('each login attempt opens a fresh invalidation window before the request starts', () => {
  assert.match(
    source,
    /const authWindowToken = beginAuthAttempt\(\)[\s\S]*?const user = mode === 'login'[\s\S]*?await login/,
  )
  assert.match(
    source,
    /await onAuthenticated\(user, authWindowToken\)/,
  )
})

test('an invalidation 401 does not add a duplicate form error beside the stored reason', () => {
  assert.match(
    source,
    /catch \(submitError\) \{[\s\S]*?isAuthWindowTokenCurrent\(authWindowToken\)[\s\S]*?!getActiveSessionInvalidation\(\)[\s\S]*?setError\(getErrorMessage\(submitError\.code\)\)/,
  )
})
