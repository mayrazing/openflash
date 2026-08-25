import test from 'node:test'
import assert from 'node:assert/strict'

import { buildItemKey } from './practiceSession.js'
import { serializeHistoryEntry } from './practiceQueue.js'

function makeCard(id, label) {
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

function makeItem(cardId, direction = 'a2b') {
  return {
    itemKey: buildItemKey(cardId, direction, 'base', 0),
    cardId,
    direction,
    kind: 'base',
    ordinal: 0,
    isNew: true,
    isReview: false,
    isRepractice: false,
    replayOnly: false,
    retryCount: 1,
    card: makeCard(cardId, `card-${cardId}`),
  }
}

test('serializeHistoryEntry 只给当前被修改卡保留 card snapshot，其他 queue item 只保留身份字段', () => {
  const currentItem = makeItem(11)
  const otherItem = makeItem(22, 'b2a')

  const result = serializeHistoryEntry({
    queue: [currentItem, otherItem],
    current: 0,
    cardId: 11,
    direction: 'a2b',
  })

  assert.equal(result.queueItems.length, 2)
  assert.deepEqual(result.queueItems[0], {
    itemKey: currentItem.itemKey,
    cardId: 11,
    direction: 'a2b',
    kind: 'base',
    ordinal: 0,
    isNew: true,
    isReview: false,
    isRepractice: false,
    replayOnly: false,
    retryCount: 1,
    card: currentItem.card,
  })
  assert.deepEqual(result.queueItems[1], {
    itemKey: otherItem.itemKey,
    cardId: 22,
    direction: 'b2a',
    kind: 'base',
    ordinal: 0,
    isNew: true,
    isReview: false,
    isRepractice: false,
    replayOnly: false,
    retryCount: 1,
    card: null,
  })
})
