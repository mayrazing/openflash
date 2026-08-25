import test from 'node:test'
import assert from 'node:assert/strict'

import { restorePracticeHistoryEntries, restorePracticeQueueItems } from './usePracticePersistence.js'

function makeCard(id, label = `card-${id}`) {
  return {
    id,
    sideA: `${label}-A`,
    sideB: `${label}-B`,
    state: 'learning',
    directionProgresses: {
      a2b: { due: '2026-07-01', fsrs: { difficulty: 3.1 } },
      b2a: { due: '2026-07-02', fsrs: { difficulty: 3.4 } },
    },
  }
}

test('restorePracticeHistoryEntries 用 getCard 补齐缺卡 history item，并与主 queue 共用 cache', async () => {
  const calls = []
  const cards = new Map([
    ['101', makeCard(101)],
    ['202', makeCard(202)],
  ])
  const getCard = async cardId => {
    calls.push(cardId)
    return cards.get(String(cardId))
  }
  const cardCache = new Map()

  const queue = await restorePracticeQueueItems([
    {
      itemKey: '101:a2b:base:0',
      cardId: 101,
      direction: 'a2b',
      kind: 'base',
      ordinal: 0,
      isNew: true,
      isReview: false,
      card: null,
    },
  ], { cardCache, getCard })

  const history = await restorePracticeHistoryEntries([
    {
      queueItems: [
        {
          itemKey: '101:a2b:base:0',
          cardId: 101,
          direction: 'a2b',
          kind: 'base',
          ordinal: 0,
          isNew: true,
          isReview: false,
          card: null,
        },
        {
          itemKey: '202:b2a:base:0',
          cardId: 202,
          direction: 'b2a',
          kind: 'base',
          ordinal: 0,
          isNew: false,
          isReview: true,
          card: null,
        },
      ],
      current: 1,
    },
  ], { cardCache, getCard })

  assert.equal(queue[0].card.sideA, 'card-101-A')
  assert.equal(history[0].queue[0].card.sideA, 'card-101-A')
  assert.equal(history[0].queue[1].card.sideA, 'card-202-A')
  assert.deepEqual(calls, [101, 202])
})
