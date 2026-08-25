import assert from 'node:assert/strict'
import test from 'node:test'
import { api, buildApiUrl, normalizeApiPayload, requestJson } from '../src/apiClient.js'
import { resetLanguage, setLanguage } from '../src/i18n.js'

test('buildApiUrl joins base URL and API path', () => {
  assert.equal(buildApiUrl('http://localhost:5173/', '/api/decks'), 'http://localhost:5173/api/decks')
})

test('normalizeApiPayload returns data for success payload', () => {
  assert.deepEqual(normalizeApiPayload({ code: 200, data: { ok: true } }), { ok: true })
})

test('normalizeApiPayload maps known API error codes to user messages', () => {
  resetLanguage()

  assert.throws(
    () => normalizeApiPayload({ code: 50301, data: null }),
    (error) => error.code === 50301 && error.message === 'This feature is not yet available',
  )
})

test('settings reads user settings endpoint', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify({ code: 200, data: { language: 'zh' } }), { status: 200 })
  }

  try {
    const settings = await api.settings('http://localhost:5173/')

    assert.deepEqual(settings, { language: 'zh' })
    assert.equal(calls[0].url, 'http://localhost:5173/api/settings')
    assert.equal(calls[0].options.credentials, 'include')
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('normalizeApiPayload maps error codes through current language', () => {
  try {
    resetLanguage()
    setLanguage('zh')
    assert.throws(
      () => normalizeApiPayload({ code: 50301, data: null }),
      (error) => error.code === 50301 && error.message === '该功能暂未开放',
    )
  } finally {
    resetLanguage()
  }
})

test('requestJson maps HTTP 401 through current language', async () => {
  const originalFetch = globalThis.fetch
  resetLanguage()
  setLanguage('de')
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 40101, data: null }), { status: 401 })

  try {
    await assert.rejects(
      requestJson('http://localhost:5173/', '/api/decks'),
      (error) => error.code === 40101 && error.message === 'Bitte zuerst anmelden',
    )
  } finally {
    globalThis.fetch = originalFetch
    resetLanguage()
  }
})

test('duplicate card error maps to user-facing text in every supported language', () => {
  try {
    const expected = {
      zh: '卡片已存在',
      en: 'Card already exists',
      fi: 'Kortti on jo olemassa',
      de: 'Karte existiert bereits',
    }
    for (const [language, message] of Object.entries(expected)) {
      setLanguage(language)
      assert.throws(
        () => normalizeApiPayload({ code: 40010, data: null }),
        (error) => error.code === 40010 && error.message === message,
      )
    }
  } finally {
    resetLanguage()
  }
})

test('logout posts to auth logout endpoint', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(JSON.stringify({ code: 200, data: null }), { status: 200 })
  }

  try {
    await api.logout('http://localhost:5173/')
    assert.equal(calls[0].url, 'http://localhost:5173/api/auth/logout')
    assert.equal(calls[0].options.method, 'POST')
    assert.equal(calls[0].options.credentials, 'include')
  } finally {
    globalThis.fetch = originalFetch
  }
})
