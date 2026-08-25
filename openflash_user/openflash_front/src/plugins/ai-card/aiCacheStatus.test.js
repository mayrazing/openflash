import test from 'node:test'
import assert from 'node:assert/strict'
import {
  AI_CACHE_STATUS_HIT,
  AI_CACHE_STATUS_QUEUED,
  openAiCacheOrNotify,
  parseAiCacheReadyNotificationData,
} from './aiCacheStatus.js'
import i18n from '../../i18n.js'

globalThis.CustomEvent = class CustomEvent {
  constructor(type, options = {}) {
    this.type = type
    this.detail = options.detail
  }
}

test('openAiCacheOrNotify opens hit content', async () => {
  let opened = null
  const events = []

  const result = await openAiCacheOrNotify({
    cardId: 1,
    side: 'b',
    title: '苹果',
    checkAiCacheStatus: async (cardId, side) => {
      assert.equal(cardId, 1)
      assert.equal(side, 'b')
      return { status: AI_CACHE_STATUS_HIT, content: 'markdown' }
    },
    dispatchEvent: (event) => events.push(event),
    onHit: (detail) => { opened = detail },
  })

  assert.equal(result, AI_CACHE_STATUS_HIT)
  assert.deepEqual(opened, { title: '苹果', markdown: 'markdown' })
  assert.deepEqual(events, [])
})

test('openAiCacheOrNotify dispatches queued for queued status', async () => {
  const events = []

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => ({ status: AI_CACHE_STATUS_QUEUED }),
    dispatchEvent: (event) => events.push(event),
    onHit: () => {
      throw new Error('hit should not open')
    },
  })

  assert.equal(result, AI_CACHE_STATUS_QUEUED)
  assert.equal(events[0].type, 'ai-queued')
})

test('openAiCacheOrNotify dispatches error for unknown status', async () => {
  const events = []

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => ({ status: 'done' }),
    dispatchEvent: (event) => events.push(event),
    onHit: () => {
      throw new Error('hit should not open')
    },
  })

  assert.equal(result, 'error')
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, i18n.t('aiCache.errorMessage'))
})

test('openAiCacheOrNotify dispatches AI_NOT_CONFIGURED message when error code is 40052', async () => {
  const events = []
  const err = new Error('not configured')
  err.code = 40052

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => { throw err },
    dispatchEvent: (event) => events.push(event),
  })

  assert.equal(result, 'error')
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, i18n.t('errors.40052'))
})

test('openAiCacheOrNotify dispatches explanation setup message for disabled status', async () => {
  const events = []

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => ({
      status: 'disabled',
      errorCode: 40054,
      sideCompletionSetupRequired: false,
    }),
    dispatchEvent: (event) => events.push(event),
  })

  assert.equal(result, 'error')
  assert.equal(events.length, 1)
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, i18n.t('errors.40054'))
})

test('openAiCacheOrNotify dispatches combined setup message when explanation and completion are disabled', async () => {
  const events = []

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => ({
      status: 'disabled',
      errorCode: 40054,
      sideCompletionSetupRequired: true,
    }),
    dispatchEvent: (event) => events.push(event),
  })

  assert.equal(result, 'error')
  assert.equal(events.length, 1)
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, i18n.t('aiCache.explanationAndCompletionDisabledMessage'))
})

test('openAiCacheOrNotify shows completion setup message without blocking hit content', async () => {
  const events = []
  let opened = null

  const result = await openAiCacheOrNotify({
    cardId: 1,
    title: 'Apple',
    checkAiCacheStatus: async () => ({
      status: AI_CACHE_STATUS_HIT,
      content: 'markdown',
      sideCompletionSetupRequired: true,
    }),
    dispatchEvent: (event) => events.push(event),
    onHit: (detail) => { opened = detail },
  })

  assert.equal(result, AI_CACHE_STATUS_HIT)
  assert.deepEqual(opened, { title: 'Apple', markdown: 'markdown' })
  assert.equal(events.length, 1)
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, i18n.t('aiCache.completionDisabledMessage'))
})

test('openAiCacheOrNotify dispatches default message when error code is unknown', async () => {
  const events = []
  const err = new Error('unknown')
  err.code = 99999

  const result = await openAiCacheOrNotify({
    cardId: 1,
    checkAiCacheStatus: async () => { throw err },
    dispatchEvent: (event) => events.push(event),
    errorMessage: 'AI 请求失败，请重试',
  })

  assert.equal(result, 'error')
  assert.equal(events[0].type, 'ai-error')
  assert.equal(events[0].detail.message, 'AI 请求失败，请重试')
})

test('parseAiCacheReadyNotificationData accepts valid SSE payload', () => {
  const payload = parseAiCacheReadyNotificationData(JSON.stringify({
    cardId: 11,
    deckId: 13,
    cardTitle: 'Apple',
    side: 'a',
  }))

  assert.deepEqual(payload, {
    cardId: 11,
    deckId: 13,
    cardTitle: 'Apple',
    side: 'a',
  })
})

test('parseAiCacheReadyNotificationData rejects payload without required ids', () => {
  assert.equal(parseAiCacheReadyNotificationData(JSON.stringify({ deckId: 13 })), null)
  assert.equal(parseAiCacheReadyNotificationData(JSON.stringify({ cardId: 11 })), null)
})

test('parseAiCacheReadyNotificationData normalizes optional fields and drops content', () => {
  const payload = parseAiCacheReadyNotificationData(JSON.stringify({
    cardId: 11,
    deckId: 13,
    content: 'markdown should not travel in notification',
  }))

  assert.deepEqual(payload, {
    cardId: 11,
    deckId: 13,
    cardTitle: i18n.t('aiCache.cardTitle'),
    side: undefined,
  })
})

test('parseAiCacheReadyNotificationData rejects invalid JSON', () => {
  assert.equal(parseAiCacheReadyNotificationData('{'), null)
})
