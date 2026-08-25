import test from 'node:test'
import assert from 'node:assert/strict'

import { buildItemKey, createRetryItems } from '../src/lib/practiceSession.js'
import {
  TODAY_REPRACTICE_MODE,
  buildSessionWithCurrentSettings,
} from '../src/lib/practiceSessionSettings.js'

function makeItem(cardId, {
  direction = 'a2b',
  kind = 'base',
  ordinal = 0,
  isNew = true,
  isReview = !isNew,
  isRepractice = kind !== 'base',
} = {}) {
  return {
    itemKey: buildItemKey(cardId, direction, kind, ordinal),
    cardId,
    direction,
    kind,
    ordinal,
    isNew,
    isReview,
    isRepractice,
  }
}

function makeSession(overrides = {}) {
  return {
    mode: 'random',
    queueItems: [makeItem(1)],
    current: 0,
    revealed: false,
    practiceFinished: false,
    firstRatedIds: [],
    stats: { newCount: 1, reviewCountStat: 0 },
    history: [],
    pendingReplay: null,
    settingsNewCardsPerDay: 1,
    settingsReviewLoadProfile: 'relaxed',
    sessionSchemaVersion: 2,
    sessionDate: '2026-06-18',
    ...overrides,
  }
}

function currentSettings(overrides = {}) {
  return {
    newCardsPerDay: 1,
    reviewLoadProfile: 'relaxed',
    ...overrides,
  }
}

test('review load change refetches queue even when new card limit is unchanged', async () => {
  let called = false
  const result = await buildSessionWithCurrentSettings(
    makeSession(),
    currentSettings({ newCardsPerDay: 1, reviewLoadProfile: 'intensive' }),
    42,
    {
      buildDailyQueue: async (deckId, newCardsLimit, mode) => {
        called = true
        assert.equal(deckId, 42)
        assert.equal(newCardsLimit, 1)
        assert.equal(mode, 'random')
        return {
          items: [
            makeItem(1),
          ],
        }
      },
    }
  )

  assert.equal(called, true)
  assert.equal(result.changed, true)
  assert.equal(result.appendedCardCount, 0)
  assert.deepEqual(result.session.queueItems.map(item => item.cardId), [1])
  assert.equal(result.session.settingsNewCardsPerDay, 1)
  assert.equal(result.session.settingsReviewLoadProfile, 'intensive')
  assert.equal(result.session.stats.newCount, 1)
})

test('review load decrease keeps existing review and retry cards', async () => {
  const retryItem = createRetryItems(makeItem(8, { isNew: false, isReview: true }), 'retry', 1)[0]
  const session = makeSession({
    queueItems: [
      makeItem(1),
      makeItem(2),
      makeItem(9, { isNew: false, isReview: true }),
      retryItem,
    ],
    settingsNewCardsPerDay: 2,
    settingsReviewLoadProfile: 'intensive',
  })

  const result = await buildSessionWithCurrentSettings(
    session,
    currentSettings({ newCardsPerDay: 2, reviewLoadProfile: 'relaxed' }),
    42,
    {
      buildDailyQueue: async () => ({ items: [] }),
    }
  )

  assert.equal(result.changed, true)
  assert.deepEqual(result.session.queueItems.map(item => item.cardId), [1, 2, 9, 8])
  assert.equal(result.session.queueItems.find(item => item.cardId === 9).isReview, true)
  assert.equal(result.session.queueItems.find(item => item.cardId === 8).isRepractice, true)
  assert.equal(result.session.settingsReviewLoadProfile, 'relaxed')
})

test('ordinary retry phase still appends new cards when session is unfinished', async () => {
  const retryItem = createRetryItems(makeItem(1, { isNew: false, isReview: true }), 'retry', 1)[0]
  const result = await buildSessionWithCurrentSettings(
    makeSession({
      queueItems: [retryItem],
      settingsNewCardsPerDay: 2,
      settingsReviewLoadProfile: 'standard',
    }),
    currentSettings({ newCardsPerDay: 2, reviewLoadProfile: 'intensive' }),
    42,
    {
      buildDailyQueue: async () => ({
        items: [
          makeItem(3),
        ],
      }),
    }
  )

  assert.equal(result.changed, true)
  assert.equal(result.appendedCardCount, 1)
  assert.deepEqual(result.session.queueItems.map(item => item.cardId), [1, 3])
  assert.equal(result.session.queueItems[0].isRepractice, true)
  assert.equal(result.session.queueItems[1].isNew, true)
})

test('today repractice session does not refetch or append new cards', async () => {
  const retryItem = createRetryItems(makeItem(1, { isNew: false, isReview: true }), TODAY_REPRACTICE_MODE, 1)[0]
  const result = await buildSessionWithCurrentSettings(
    makeSession({
      mode: TODAY_REPRACTICE_MODE,
      queueItems: [retryItem],
      settingsNewCardsPerDay: 2,
      settingsReviewLoadProfile: 'standard',
    }),
    currentSettings({ newCardsPerDay: 2, reviewLoadProfile: 'intensive' }),
    42,
    {
      buildDailyQueue: async () => {
        throw new Error('today repractice must not refetch queue')
      },
    }
  )

  assert.equal(result.changed, true)
  assert.deepEqual(result.session.queueItems.map(item => item.cardId), [1])
  assert.equal(result.session.settingsReviewLoadProfile, 'intensive')
})

test('legacy session without review load profile restores and records current profile', async () => {
  const session = makeSession({
    settingsReviewLoadProfile: undefined,
    settingsNewCardsPerDay: 1,
  })
  delete session.settingsReviewLoadProfile

  const result = await buildSessionWithCurrentSettings(
    session,
    currentSettings({ newCardsPerDay: 1, reviewLoadProfile: 'standard' }),
    42,
    {
      buildDailyQueue: async () => {
        throw new Error('standard legacy profile does not need fresh queue')
      },
    }
  )

  assert.equal(result.changed, true)
  assert.deepEqual(result.session.queueItems.map(item => item.cardId), [1])
  assert.equal(result.session.settingsReviewLoadProfile, 'standard')
})
