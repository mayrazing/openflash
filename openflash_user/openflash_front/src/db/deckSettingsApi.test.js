import { test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import JSZip from 'jszip'
import {
  exportDecks,
  getDeckSettings,
  getLanguageOptions,
  getPracticeStartupSnapshot,
  getTodayCardsByDeck,
  getToday,
  rateCardFsrs,
  saveDeckSettings,
  saveSettings,
  shouldCardRepeatToday,
  shouldCardRepeatTomorrow,
} from './database.js'

const originalFetch = globalThis.fetch
const originalDocument = globalThis.document
const originalWindow = globalThis.window
const originalCustomEvent = globalThis.CustomEvent
const originalCreateObjectUrl = globalThis.URL?.createObjectURL
const originalRevokeObjectUrl = globalThis.URL?.revokeObjectURL

beforeEach(() => {
  globalThis.fetch = async () => {
    throw new Error('fetch mock not configured')
  }
})

afterEach(() => {
  globalThis.fetch = originalFetch
  globalThis.document = originalDocument
  globalThis.window = originalWindow
  globalThis.CustomEvent = originalCustomEvent
  globalThis.URL.createObjectURL = originalCreateObjectUrl
  globalThis.URL.revokeObjectURL = originalRevokeObjectUrl
})

test('rateCardFsrs only sends the rating fields required by the backend', async () => {
  let capturedBody = null
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/cards/12/reviews')
    assert.equal(options.method, 'POST')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: {} })
  }

  await rateCardFsrs(12, '12:a2b', 'a2b', 3, 0.71)

  assert.deepEqual(capturedBody, {
    itemKey: '12:a2b',
    direction: 'a2b',
    rating: 3,
  })
})

test('getDeckSettings reads deck scoped settings and fills missing defaults', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/decks/42/settings')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['Content-Type'], 'application/json')
    return response({
      code: 200,
      data: {
        deckId: 42,
        newCardsPerDay: 18,
        targetRetention: 0.88,
        reviewLoadProfile: 'intensive',
      },
    })
  }

  const settings = await getDeckSettings(42)

  assert.equal(settings.newCardsPerDay, 18)
  assert.equal(settings.targetRetention, 0.88)
  assert.equal(settings.reviewLoadProfile, 'intensive')
  assert.equal(settings.duplicateSideAEnabled, true)
  assert.equal(settings.duplicateSideBEnabled, false)
})

test('graduated cards never count as today or tomorrow review cards', () => {
  const card = {
    state: 'graduated',
    todayCalculated: true,
    fsrs: {
      nextReviewDate: getToday(),
      lastReviewDate: getToday(),
    },
  }

  assert.equal(shouldCardRepeatToday(card, getToday()), false)
  assert.equal(shouldCardRepeatTomorrow(card, getToday()), false)
})

test('saveDeckSettings sends full deck scoped payload to the deck settings endpoint', async () => {
  const payload = {
    newCardsPerDay: 7,
    targetRetention: 0.93,
    reviewLoadProfile: 'relaxed',
    duplicateSideAEnabled: false,
    duplicateSideBEnabled: true,
  }
  let capturedBody = null
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/decks/42/settings')
    assert.equal(options.method, 'PUT')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: payload })
  }

  const saved = await saveDeckSettings(42, payload)

  assert.deepEqual(capturedBody, payload)
  assert.deepEqual(saved, payload)
})

test('exportDecks includes deck scoped settings in decks.json', async () => {
  const calls = []
  globalThis.fetch = async (url) => {
    calls.push(url)
    if (url === '/api/decks/42') {
      return response({ code: 200, data: { id: 42, name: 'Book' } })
    }
    if (url === '/api/decks/42/cards') {
      return response({ code: 200, data: [] })
    }
    if (url === '/api/decks/42/settings') {
      return response({
        code: 200,
        data: {
          deckId: 42,
          newCardsPerDay: 6,
          targetRetention: 0.84,
          reviewLoadProfile: 'relaxed',
          duplicateSideAEnabled: false,
          duplicateSideBEnabled: true,
        },
      })
    }
    throw new Error(`unexpected url: ${url}`)
  }
  let exportedBlob = null
  globalThis.URL.createObjectURL = (blob) => {
    exportedBlob = blob
    return 'blob:deck-export'
  }
  globalThis.URL.revokeObjectURL = () => {}
  globalThis.document = {
    createElement: () => ({
      set href(value) { this._href = value },
      get href() { return this._href },
      set download(value) { this._download = value },
      get download() { return this._download },
      click() {},
    }),
  }

  await exportDecks([42])

  assert.deepEqual(calls.sort(), [
    '/api/decks/42',
    '/api/decks/42/cards',
    '/api/decks/42/settings',
  ].sort())
  const zip = await JSZip.loadAsync(await exportedBlob.arrayBuffer())
  const payload = JSON.parse(await zip.file('decks.json').async('string'))
  assert.deepEqual(payload.decks[0].settings, {
    newCardsPerDay: 6,
    targetRetention: 0.84,
    reviewLoadProfile: 'relaxed',
    duplicateSideAEnabled: false,
    duplicateSideBEnabled: true,
  })
})

test('saveSettings does not publish legacy live settings events', async () => {
  let dispatched = false
  globalThis.CustomEvent = class {
    constructor(name, options) {
      this.type = name
      this.detail = options?.detail
    }
  }
  globalThis.window = {
    dispatchEvent(event) {
      if (event.type === 'pick-word-settings-changed') dispatched = true
    },
    localStorage: {
      setItem() {},
    },
  }
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings')
    assert.equal(options.method, 'PUT')
    return response({ code: 200, data: { theme: 'dark', soundEnabled: false } })
  }

  await saveSettings({ theme: 'dark', soundEnabled: false })

  assert.equal(dispatched, false)
})

test('getLanguageOptions reads settings language registry options', async () => {
  const options = [
    { value: 'en', label: 'English' },
    { value: 'fi', label: 'Suomi' },
  ]
  globalThis.fetch = async (url, requestOptions) => {
    assert.equal(url, '/api/settings/languages')
    assert.equal(requestOptions.credentials, 'include')
    return response({ code: 200, data: options })
  }

  const result = await getLanguageOptions()

  assert.deepEqual(result, options)
})

test('getLanguageOptions returns empty list when response data is not a list', async () => {
  globalThis.fetch = async () => response({ code: 200, data: null })

  const result = await getLanguageOptions()

  assert.deepEqual(result, [])
})

test('exportDecks starts deck cards and settings requests in the same wave', async () => {
  const calls = []
  const pending = {}
  globalThis.fetch = async (url) => {
    calls.push(url)
    if (url === '/api/decks/42') {
      return new Promise(resolve => { pending.deck = () => resolve(response({ code: 200, data: { id: 42, name: 'Book' } })) })
    }
    if (url === '/api/decks/42/settings') {
      return new Promise(resolve => {
        pending.settings = () => resolve(response({
          code: 200,
          data: {
            deckId: 42,
            newCardsPerDay: 6,
            targetRetention: 0.84,
            reviewLoadProfile: 'relaxed',
            duplicateSideAEnabled: false,
            duplicateSideBEnabled: true,
          },
        }))
      })
    }
    if (url === '/api/decks/42/cards') {
      return response({ code: 200, data: [] })
    }
    throw new Error(`unexpected url: ${url}`)
  }
  globalThis.URL.createObjectURL = () => 'blob:deck-export'
  globalThis.URL.revokeObjectURL = () => {}
  globalThis.document = {
    createElement: () => ({
      click() {},
    }),
  }

  const exportPromise = exportDecks([42])
  await Promise.resolve()
  await Promise.resolve()

  assert.deepEqual(calls.sort(), [
    '/api/decks/42',
    '/api/decks/42/cards',
    '/api/decks/42/settings',
  ].sort())
  pending.deck()
  pending.settings()
  await exportPromise
})

test('getPracticeStartupSnapshot reuses one pending item load for summary and today cards', async () => {
  const calls = []
  globalThis.fetch = async (url) => {
    calls.push(url)
    if (url === '/api/decks/42/cards') {
      return response({
        code: 200,
        data: [
          { id: 1, state: 'learning', firstLearnedDate: getToday() },
          { id: 2, state: 'new' },
        ],
      })
    }
    if (url === '/api/session-store/42/session') {
      return response({ code: 200, data: null })
    }
    if (url === '/api/decks/42/practice/queue?newCardsLimit=10&mode=random') {
      return response({
        code: 200,
        data: {
          items: [{ itemKey: '2:a2b:base:0', cardId: 2, isNew: true, direction: 'a2b' }],
        },
      })
    }
    if (url === '/api/decks/42/practice/summary?newCardsLimit=10') {
      return response({
        code: 200,
        data: { pendingBacklog: 0, newCardsPaused: false, targetReviewItemCount: 40, maxReviewItemCount: 70 },
      })
    }
    throw new Error(`unexpected url: ${url}`)
  }

  const snapshot = await getPracticeStartupSnapshot(42, 10)

  assert.equal(calls.filter(url => url === '/api/decks/42/cards').length, 1)
  assert.equal(calls.filter(url => url === '/api/session-store/42/session').length, 1)
  assert.equal(snapshot.pendingSummary.pendingTotal, 1)
  assert.equal(snapshot.todayCards.length, 2)
})

test('getTodayCardsByDeck uses backend today card endpoint', async () => {
  const calls = []
  globalThis.fetch = async (url) => {
    calls.push(url)
    if (url === '/api/decks/42/today-cards?newCardsLimit=10') {
      return response({ code: 200, data: [{ id: 1 }, { id: 2 }] })
    }
    throw new Error(`unexpected url: ${url}`)
  }

  const cards = await getTodayCardsByDeck(42, 10)

  assert.deepEqual(calls, ['/api/decks/42/today-cards?newCardsLimit=10'])
  assert.equal(cards.length, 2)
})

function response(body) {
  return {
    status: 200,
    ok: true,
    text: async () => JSON.stringify(body),
  }
}
