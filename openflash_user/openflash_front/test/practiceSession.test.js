import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createEmptyPracticeSessionState,
  applyRating,
  applyRetry,
  applyRetrySlowRecall,
  applyFamiliar,
  buildItemKey,
  createRetryItems,
  markDistributionBaseResult,
  markDistributionRetryResult,
  noConsecutiveShuffle,
} from '../src/lib/practiceSession.js'

function makeItem(cardId, direction = 'a2b', kind = 'base', ordinal = 0, extra = {}) {
  return {
    itemKey: buildItemKey(cardId, direction, kind, ordinal),
    cardId,
    direction,
    kind,
    ordinal,
    isNew: true,
    isReview: false,
    isRepractice: kind !== 'base',
    card: {
      id: cardId,
      sideA: `front ${cardId}`,
      sideB: `back ${cardId}`,
    },
    ...extra,
  }
}

function makeState(overrides = {}) {
  const baseState = createEmptyPracticeSessionState()
  return {
    ...baseState,
    queue: [makeItem(1), makeItem(2)],
    ...overrides,
    stats: {
      ...baseState.stats,
      ...(overrides.stats ?? {}),
    },
    cardProgress: {
      ...baseState.cardProgress,
      ...(overrides.cardProgress ?? {}),
    },
    distribution: overrides.distribution ?? baseState.distribution,
    firstRatedIds: overrides.firstRatedIds ?? baseState.firstRatedIds,
  }
}

function getDistributionDirection(state, cardId, direction = 'a2b') {
  return state.distribution.cards.find(card => card.cardId === cardId)?.directions[direction]
}

async function withMockedRandom(values, callback) {
  const originalRandom = Math.random
  let index = 0
  Math.random = () => {
    const value = values[index] ?? values.at(-1) ?? 0
    index += 1
    return value
  }

  try {
    return await callback()
  } finally {
    Math.random = originalRandom
  }
}

test('applyRating rating=2: 前进到下一张，插入1个重试项', () => {
  const item = makeItem(1)
  const state = makeState({ queue: [item, makeItem(2)] })

  const result = applyRating(state, item, 2)

  assert.equal(result.current, 1)
  assert.equal(result.nextIndex, 1)
  assert.equal(result.nextFinished, false)
  assert.equal(result.queue.length, 3)
  assert.equal(result.retryQueueItems.length, 1)
  assert.equal(result.stats.good, 1)
  assert.equal(getDistributionDirection(result, 1).retryActive, true)
  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false])
})

test('applyRating rating=3: 记录到 firstRatedIds', () => {
  const item = makeItem(1, 'b2a')
  const state = makeState({ queue: [item, makeItem(2)] })

  const result = applyRating(state, item, 3)

  assert.deepEqual(result.firstRatedIds, ['1:b2a'])
  assert.equal(result.stats.easy, 1)
})

test('applyRating rating=2: 同一方向二次评分不重复计入 stats', () => {
  const item = makeItem(1)
  const state = makeState({
    queue: [item, makeItem(2)],
    firstRatedIds: ['1:a2b'],
    stats: { good: 7 },
  })

  const result = applyRating(state, item, 2)

  assert.deepEqual(result.firstRatedIds, ['1:a2b'])
  assert.equal(result.stats.good, 7)
  assert.equal(result.current, 1)
})

test('applyRating mastered=true: 不再加入会了收集本确认队列', () => {
  const item = makeItem(1)
  const state = makeState({ queue: [item, makeItem(1, 'b2a'), makeItem(2)] })
  const masteredCardInfo = { id: 1, sideA: 'front 1', sideB: 'back 1' }

  const result = applyRating(state, item, 3, {
    mastered: true,
    masteredCardInfo,
  })

  assert.deepEqual(result.masteredQueue, [])
  assert.deepEqual(result.queue.map(queueItem => queueItem.itemKey), [item.itemKey, buildItemKey(1, 'b2a'), buildItemKey(2, 'a2b')])
  assert.equal(result.stats.masteredCount, 0)
  assert.equal(result.nextFinished, false)
})

test('applyRating mastered=true: 按普通评分更新当前方向', () => {
  const frontItem = makeItem(1, 'a2b')
  const backItem = makeItem(1, 'b2a')
  const afterFront = applyRating(
    makeState({ queue: [frontItem, backItem, makeItem(2)] }),
    frontItem,
    2
  )

  const result = applyRating(afterFront, backItem, 3, {
    mastered: true,
    masteredCardInfo: { id: 1, sideA: 'front 1', sideB: 'back 1' },
  })

  assert.deepEqual(getDistributionDirection(result, 1, 'a2b').retryCompleted, [false])
  assert.equal(getDistributionDirection(result, 1, 'a2b').completed, false)
  assert.equal(getDistributionDirection(result, 1, 'b2a').completed, true)
  assert.deepEqual(result.masteredQueue, [])
})

test('applyRating rating=0: 插入3个重试项到队列尾部', () => {
  const item = makeItem(1)
  const state = makeState({ queue: [item, makeItem(2)] })

  const result = applyRating(state, item, 0)
  const retryKeys = createRetryItems(item).map(retryItem => retryItem.itemKey)
  const resultRetryKeys = result.queue
    .filter(queueItem => queueItem.isRepractice)
    .map(queueItem => queueItem.itemKey)

  assert.equal(result.queue.length, 5)
  assert.equal(result.retryQueueItems.length, 3)
  assert.deepEqual(resultRetryKeys.toSorted(), retryKeys.toSorted())
  assert.equal(result.stats.again, 1)
})

test('applyRating rating=1: distribution 标记为 retryActive', () => {
  const item = makeItem(1)
  const state = makeState({ queue: [item, makeItem(2)] })

  const result = applyRating(state, item, 1)
  const directionState = getDistributionDirection(result, 1)

  assert.equal(directionState.retryActive, true)
  assert.equal(directionState.completed, false)
  assert.deepEqual(directionState.retryCompleted, [false, false])
  assert.equal(result.stats.hard, 1)
})

test('applyRating rating=0: 队列只有1张时 nextFinished=false（有重试项）', () => {
  const item = makeItem(1)
  const state = makeState({ queue: [item] })

  const result = applyRating(state, item, 0)

  assert.equal(result.nextFinished, false)
  assert.equal(result.current, 1)
  assert.equal(result.queue.length, 4)
  assert.equal(result.queue.slice(1).every(queueItem => queueItem.isRepractice), true)
})

test('applyRating rating=2: 重练项不贴着同卡另一方向出现', async () => {
  const item = makeItem(1, 'a2b')
  const sameCardBack = makeItem(1, 'b2a')
  const state = makeState({
    queue: [item, makeItem(2), makeItem(3), sameCardBack],
  })

  const result = await withMockedRandom([0.99, 0.99], () => (
    applyRating(state, item, 2)
  ))

  assert.equal(result.queue.length, 5)
  assert.equal(result.queue.some((queueItem, index) => (
    index > 0 && queueItem.cardId === result.queue[index - 1].cardId
  )), false)
})

test('noConsecutiveShuffle: 重练题先整体随机，再修正相邻同卡', async () => {
  const queue = [
    makeItem(1, 'a2b', 'retry', 0),
    makeItem(1, 'a2b', 'retry', 1),
    makeItem(1, 'a2b', 'retry', 2),
    makeItem(2, 'a2b', 'retry', 0),
    makeItem(2, 'a2b', 'retry', 1),
    makeItem(2, 'a2b', 'retry', 2),
    makeItem(3, 'a2b', 'retry', 0),
    makeItem(3, 'a2b', 'retry', 1),
    makeItem(3, 'a2b', 'retry', 2),
  ]

  const result = await withMockedRandom([0, 0, 0, 0, 0, 0, 0, 0], () => (
    noConsecutiveShuffle(queue)
  ))

  assert.equal(result.length, queue.length)
  assert.deepEqual(result.map(item => item.itemKey).toSorted(), queue.map(item => item.itemKey).toSorted())
  assert.equal(result.some((item, index) => index > 0 && item.cardId === result[index - 1].cardId), false)
  assert.notEqual(new Set(result.slice(0, 3).map(item => item.cardId)).size, 3)
})

test('applyRetry passed=true: 前进到下一张', () => {
  const item = makeItem(1, 'a2b', 'retry', 0)
  const state = makeState({ queue: [item, makeItem(2)] })

  const result = applyRetry(state, item, true)

  assert.equal(result.current, 1)
  assert.equal(result.nextIndex, 1)
  assert.equal(result.nextFinished, false)
  assert.equal(getDistributionDirection(result, 1).retryCompleted[0], true)
})

test('applyRetry passed=false: 1/2降为0/2且不增加总次数', () => {
  const passedItem = makeItem(1, 'a2b', 'retry', 0, { retryCount: 2 })
  const item = makeItem(1, 'a2b', 'retry', 1, { retryCount: 2 })
  const distribution = markDistributionRetryResult(
    markDistributionBaseResult(
      createEmptyPracticeSessionState().distribution,
      makeItem(1),
      false,
      2
    ),
    passedItem,
    true
  )
  const state = makeState({ queue: [item, makeItem(2)], distribution })

  const result = applyRetry(state, item, false)
  const retryItemsForCard = result.queue.filter(queueItem => (
    queueItem.cardId === 1 && queueItem.isRepractice
  ))

  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false, false])
  assert.equal(retryItemsForCard.length, 2)
  assert.equal(retryItemsForCard.every(queueItem => queueItem.retryCount === 2), true)
})

test('applyRetry passed=false: 0/2升为0/3', () => {
  const item = makeItem(1, 'a2b', 'retry', 0, { retryCount: 2 })
  const distribution = markDistributionBaseResult(
    createEmptyPracticeSessionState().distribution,
    makeItem(1),
    false,
    2
  )
  const state = makeState({ queue: [item, makeItem(2)], distribution })

  const result = applyRetry(state, item, false)

  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false, false, false])
})

test('applyRetry passed=false: 0/3保持0/3', () => {
  const item = makeItem(1, 'a2b', 'retry', 0, { retryCount: 3 })
  const distribution = markDistributionBaseResult(
    createEmptyPracticeSessionState().distribution,
    makeItem(1),
    false,
    3
  )
  const state = makeState({ queue: [item, makeItem(2)], distribution })

  const result = applyRetry(state, item, false)

  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false, false, false])
  assert.equal(result.queue.filter(queueItem => queueItem.cardId === 1 && queueItem.isRepractice).length, 3)
})

test('applyRetry passed=false: 缺retryCount时按现有重试组数量加1并封顶3', () => {
  const item = makeItem(1, 'a2b', 'retry', 1)
  const queue = [item, makeItem(2), makeItem(1, 'a2b', 'retry', 2)]
  const distribution = markDistributionBaseResult(
    createEmptyPracticeSessionState().distribution,
    makeItem(1),
    false,
    2
  )
  const state = makeState({ queue, distribution })

  const result = applyRetry(state, item, false)
  const retryItemsForCard = result.queue.filter(queueItem => (
    queueItem.cardId === 1 && queueItem.isRepractice
  ))

  assert.equal(result.queue.length, queue.length + 1)
  assert.equal(result.current, 0)
  assert.equal(result.nextFinished, false)
  assert.equal(retryItemsForCard.length, 3)
  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false, false, false])
})

test('applyRetrySlowRecall: 当前重练项后移且不改变统计和分布', () => {
  const item = makeItem(1, 'a2b', 'retry', 0, { retryCount: 2 })
  const other = makeItem(2, 'a2b', 'retry', 0, { retryCount: 1 })
  const distribution = markDistributionBaseResult(
    createEmptyPracticeSessionState().distribution,
    makeItem(1),
    false,
    2
  )
  const state = makeState({
    queue: [item, other],
    distribution,
    stats: { again: 1, hard: 2, good: 3, easy: 4 },
  })

  const result = applyRetrySlowRecall(state, item)

  assert.deepEqual(result.queue.map(queueItem => queueItem.itemKey), [other.itemKey, item.itemKey])
  assert.equal(result.current, 0)
  assert.equal(result.nextIndex, 0)
  assert.equal(result.nextFinished, false)
  assert.deepEqual(result.stats, state.stats)
  assert.deepEqual(getDistributionDirection(result, 1).retryCompleted, [false, false])
  assert.deepEqual(result.retryQueueItems.map(queueItem => queueItem.itemKey), [item.itemKey])
})

test('applyRetrySlowRecall: 后移当前重练项时不贴着同卡另一方向', () => {
  const item = makeItem(1, 'a2b', 'retry', 0, { retryCount: 2 })
  const sameCardBack = makeItem(1, 'b2a', 'retry', 0, { retryCount: 1 })
  const state = makeState({
    queue: [item, makeItem(2, 'a2b', 'retry', 0), sameCardBack, makeItem(3, 'a2b', 'retry', 0)],
  })

  const result = applyRetrySlowRecall(state, item)

  assert.equal(result.queue.some((queueItem, index) => (
    index > 0 && queueItem.cardId === result.queue[index - 1].cardId
  )), false)
  assert.equal(result.queue[result.current].cardId, 2)
})

test('applyFamiliar: 移除该卡所有后续出现，masteredCount+1', () => {
  const item = makeItem(1)
  const state = makeState({
    queue: [
      item,
      makeItem(2),
      makeItem(1, 'b2a'),
      makeItem(1, 'a2b', 'retry', 0),
      makeItem(3),
    ],
    cardProgress: {
      requiredDirectionsByCard: { 1: ['a2b', 'b2a'] },
      completedDirectionsByCard: {},
    },
    masteredQueue: [{ id: 1, sideA: 'front 1', sideB: 'back 1' }],
  })

  const result = applyFamiliar(state, item)

  assert.deepEqual(result.queue.map(queueItem => queueItem.cardId), [2, 3])
  assert.equal(result.stats.masteredCount, 1)
  assert.deepEqual(result.cardProgress.completedDirectionsByCard['1'], ['a2b', 'b2a'])
  assert.deepEqual(result.masteredQueue, [])
  assert.equal(getDistributionDirection(result, 1, 'a2b').completed, true)
  assert.equal(getDistributionDirection(result, 1, 'b2a').completed, true)
})
