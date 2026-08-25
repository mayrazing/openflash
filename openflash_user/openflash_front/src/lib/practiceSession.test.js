import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createRetryItems,
  markDistributionBaseResult,
  markDistributionRetryResult,
  rebuildRetrySet,
  applyRating,
  applyRetrySlowRecall,
  createEmptyPracticeSessionState,
  buildItemKey,
  ratingsForPracticeItem,
  pruneSavedSessionForRemovedCard,
  pruneSavedSessionForRemovedCards,
} from './practiceSession.js'

function makeItem(cardId = 1, direction = 'a2b', kind = 'base', ordinal = 0) {
  return {
    cardId,
    direction,
    kind,
    ordinal,
    itemKey: buildItemKey(cardId, direction, kind, ordinal),
    isRepractice: false,
    card: { sideA: 'test', sideB: 'test' },
  }
}

function makeRetryItem(cardId, direction, ordinal, retryCount) {
  return {
    cardId,
    direction,
    kind: 'retry',
    ordinal,
    retryCount,
    itemKey: buildItemKey(cardId, direction, 'retry', ordinal),
    isRepractice: true,
    card: { sideA: 'test', sideB: 'test' },
  }
}

function makeBaseSession(cardId = 1, direction = 'a2b') {
  const state = createEmptyPracticeSessionState()
  const item = makeItem(cardId, direction)
  state.queue = [item]
  state.current = 0
  return { state, item }
}

// ── createRetryItems ──────────────────────────────────────────────

test('createRetryItems retryCount=1 生成1个item且携带retryCount字段', () => {
  const item = makeItem()
  const items = createRetryItems(item, 'retry', 1)
  assert.equal(items.length, 1)
  assert.equal(items[0].ordinal, 0)
  assert.equal(items[0].retryCount, 1)
  assert.equal(items[0].isRepractice, true)
})

test('createRetryItems retryCount=2 生成2个item', () => {
  const item = makeItem()
  const items = createRetryItems(item, 'retry', 2)
  assert.equal(items.length, 2)
  assert.equal(items[0].retryCount, 2)
  assert.equal(items[1].retryCount, 2)
  assert.equal(items[1].ordinal, 1)
})

test('createRetryItems 默认retryCount=3行为不变', () => {
  const item = makeItem()
  const items = createRetryItems(item, 'retry', 3)
  assert.equal(items.length, 3)
  assert.equal(items[2].ordinal, 2)
})

// ── markDistributionBaseResult ────────────────────────────────────

test('markDistributionBaseResult retryCount=1 设retryCompleted=[false]', () => {
  const state = { cards: [] }
  const item = makeItem()
  const next = markDistributionBaseResult(state, item, false, 1)
  const dirState = next.cards[0].directions['a2b']
  assert.deepEqual(dirState.retryCompleted, [false])
  assert.equal(dirState.retryActive, true)
})

test('markDistributionBaseResult retryCount=2 设retryCompleted=[false,false]', () => {
  const state = { cards: [] }
  const item = makeItem()
  const next = markDistributionBaseResult(state, item, false, 2)
  const dirState = next.cards[0].directions['a2b']
  assert.deepEqual(dirState.retryCompleted, [false, false])
})

test('markDistributionBaseResult passed=true时retryCount不影响结果', () => {
  const state = { cards: [] }
  const item = makeItem()
  const next = markDistributionBaseResult(state, item, true, 1)
  const dirState = next.cards[0].directions['a2b']
  assert.equal(dirState.completed, true)
})

// ── markDistributionRetryResult ───────────────────────────────────

test('markDistributionRetryResult retryCount=1通过时retryCompleted=[true]', () => {
  const state = { cards: [] }
  const item = makeItem()
  const baseState = markDistributionBaseResult(state, item, false, 1)
  const retryItem = makeRetryItem(1, 'a2b', 0, 1)
  const next = markDistributionRetryResult(baseState, retryItem, true)
  assert.deepEqual(next.cards[0].directions['a2b'].retryCompleted, [true])
})

test('markDistributionRetryResult retryCount=2第1格通过时retryCompleted=[true,false]', () => {
  const state = { cards: [] }
  const item = makeItem()
  const baseState = markDistributionBaseResult(state, item, false, 2)
  const retryItem = makeRetryItem(1, 'a2b', 0, 2)
  const next = markDistributionRetryResult(baseState, retryItem, true)
  assert.deepEqual(next.cards[0].directions['a2b'].retryCompleted, [true, false])
})

test('markDistributionRetryResult 失败时重练次数加1并封顶3', () => {
  const state = { cards: [] }
  const item = makeItem()
  const baseState = markDistributionBaseResult(state, item, false, 2)
  const retryItem = makeRetryItem(1, 'a2b', 0, 2)
  const next = markDistributionRetryResult(baseState, retryItem, false)
  assert.deepEqual(next.cards[0].directions['a2b'].retryCompleted, [false, false, false])
})

test('markDistributionRetryResult 缺retryCount时按分布图已有次数加1', () => {
  const state = { cards: [] }
  const item = makeItem()
  const baseState = markDistributionBaseResult(state, item, false, 1)
  const retryItem = makeRetryItem(1, 'a2b', 0, 1)
  delete retryItem.retryCount
  const next = markDistributionRetryResult(baseState, retryItem, false)
  assert.deepEqual(next.cards[0].directions['a2b'].retryCompleted, [false, false])
})

// ── rebuildRetrySet ───────────────────────────────────────────────

test('rebuildRetrySet item.retryCount=1失败后重建2个retry item', () => {
  const retryItem = makeRetryItem(1, 'a2b', 0, 1)
  const queue = [retryItem]
  const result = rebuildRetrySet(queue, 0, retryItem)
  const retries = result.filter(q => q.isRepractice && q.cardId === 1)
  assert.equal(retries.length, 2)
  assert.equal(retries[0].retryCount, 2)
})

test('rebuildRetrySet item.retryCount=2失败后重建3个retry item', () => {
  const retryItem = makeRetryItem(1, 'a2b', 0, 2)
  const queue = [retryItem]
  const result = rebuildRetrySet(queue, 0, retryItem)
  const retries = result.filter(q => q.isRepractice && q.cardId === 1)
  assert.equal(retries.length, 3)
})

test('rebuildRetrySet item.retryCount=3失败后仍保持3个retry item', () => {
  const retryItem = makeRetryItem(1, 'a2b', 0, 3)
  const queue = [retryItem]
  const result = rebuildRetrySet(queue, 0, retryItem)
  const retries = result.filter(q => q.isRepractice && q.cardId === 1)
  assert.equal(retries.length, 3)
})

// ── applyRating ───────────────────────────────────────────────────

test('applyRating rating=0 生成3个retry item', () => {
  const { state, item } = makeBaseSession()
  const next = applyRating(state, item, 0)
  const retries = next.queue.filter(q => q.isRepractice && q.cardId === item.cardId)
  assert.equal(retries.length, 3)
  assert.equal(retries[0].retryCount, 3)
})

test('applyRating rating=1 生成2个retry item', () => {
  const { state, item } = makeBaseSession()
  const next = applyRating(state, item, 1)
  const retries = next.queue.filter(q => q.isRepractice && q.cardId === item.cardId)
  assert.equal(retries.length, 2)
  assert.equal(retries[0].retryCount, 2)
})

test('applyRating rating=2 生成1个retry item', () => {
  const { state, item } = makeBaseSession()
  const next = applyRating(state, item, 2)
  const retries = next.queue.filter(q => q.isRepractice && q.cardId === item.cardId)
  assert.equal(retries.length, 1)
  assert.equal(retries[0].retryCount, 1)
})

test('applyRating rating=2 distribution retryCompleted=[false]', () => {
  const { state, item } = makeBaseSession()
  const next = applyRating(state, item, 2)
  const dirState = next.distribution.cards[0]?.directions?.['a2b']
  assert.deepEqual(dirState?.retryCompleted, [false])
})

test('applyRating rating=3 不生成retry item直接通过', () => {
  const { state, item } = makeBaseSession()
  const next = applyRating(state, item, 3)
  const retries = next.queue.filter(q => q.isRepractice && q.cardId === item.cardId)
  assert.equal(retries.length, 0)
  assert.equal(next.nextFinished, true)
})

test('同一卡另一方向选完全不会或模糊后隐藏记得很清楚', () => {
  const ratings = [0, 1, 2, 3].map(value => ({ value }))

  for (const rating of [0, 1]) {
    const state = createEmptyPracticeSessionState()
    const firstDirection = makeItem(1, 'a2b')
    const oppositeDirection = makeItem(1, 'b2a')
    state.queue = [firstDirection, oppositeDirection]

    const next = applyRating(state, firstDirection, rating)

    assert.deepEqual(
      ratingsForPracticeItem(ratings, next.distribution, oppositeDirection).map(entry => entry.value),
      [0, 1, 2]
    )
  }
})

test('同一卡另一方向选想起来了或记得很清楚后保留全部评分', () => {
  const ratings = [0, 1, 2, 3].map(value => ({ value }))

  for (const rating of [2, 3]) {
    const state = createEmptyPracticeSessionState()
    const firstDirection = makeItem(1, 'b2a')
    const oppositeDirection = makeItem(1, 'a2b')
    state.queue = [firstDirection, oppositeDirection]

    const next = applyRating(state, firstDirection, rating)

    assert.deepEqual(
      ratingsForPracticeItem(ratings, next.distribution, oppositeDirection).map(entry => entry.value),
      [0, 1, 2, 3]
    )
  }
})

test('评分按钮限制按用户选择值判断而不是反应时间降档值', () => {
  const ratings = [0, 1, 2, 3].map(value => ({ value }))
  const state = createEmptyPracticeSessionState()
  const firstDirection = makeItem(1, 'a2b')
  const oppositeDirection = makeItem(1, 'b2a')
  state.queue = [firstDirection, oppositeDirection]

  const next = applyRating(state, firstDirection, 1, {}, 2)

  assert.deepEqual(
    ratingsForPracticeItem(ratings, next.distribution, oppositeDirection).map(entry => entry.value),
    [0, 1, 2, 3]
  )
})

test('applyRating graduated=true: 毕业卡移除本轮同卡后续题且不加入会了确认队列', () => {
  const state = createEmptyPracticeSessionState()
  const current = makeItem(1, 'a2b')
  const sameCardLater = makeItem(1, 'b2a')
  const otherCard = makeItem(2, 'a2b')
  state.queue = [current, sameCardLater, otherCard]
  state.current = 0
  state.masteredQueue = []

  const next = applyRating(state, current, 3, {
    graduated: true,
    refreshedCard: { id: 1, sideA: 'done', sideB: 'done', state: 'graduated' },
  })

  assert.deepEqual(next.queue.map(item => item.cardId), [1, 2])
  assert.equal(next.current, 1)
  assert.equal(next.nextFinished, false)
  assert.equal(next.masteredQueue.length, 0)
})

test('applyRating mastered=true: 不再加入会了确认队列也不移除同卡后续题', () => {
  const state = createEmptyPracticeSessionState()
  const current = makeItem(1, 'a2b')
  const sameCardLater = makeItem(1, 'b2a')
  const otherCard = makeItem(2, 'a2b')
  state.queue = [current, sameCardLater, otherCard]
  state.current = 0
  state.masteredQueue = []

  const next = applyRating(state, current, 3, {
    mastered: true,
    refreshedCard: { id: 1, sideA: 'kept', sideB: 'kept', state: 'learning' },
  })

  assert.deepEqual(next.queue.map(item => item.cardId), [1, 1, 2])
  assert.equal(next.current, 1)
  assert.equal(next.nextFinished, false)
  assert.equal(next.masteredQueue.length, 0)
})

test('applyRetrySlowRecall 后移当前重练项但不标记重练格', () => {
  const state = createEmptyPracticeSessionState()
  const current = makeRetryItem(1, 'a2b', 0, 2)
  const nextItem = makeRetryItem(2, 'a2b', 0, 1)
  state.queue = [current, nextItem]
  state.current = 0
  state.distribution = markDistributionBaseResult(
    createEmptyPracticeSessionState().distribution,
    makeItem(1),
    false,
    2
  )

  const next = applyRetrySlowRecall(state, current)

  assert.deepEqual(next.queue.map(item => item.itemKey), [nextItem.itemKey, current.itemKey])
  assert.equal(next.current, 0)
  assert.equal(next.nextFinished, false)
  assert.deepEqual(next.distribution.cards[0].directions['a2b'].retryCompleted, [false, false])
  assert.deepEqual(next.retryQueueItems.map(item => item.itemKey), [current.itemKey])
})

// ── pruneSavedSessionForRemovedCard ──────────────────────────────

test('pruneSavedSessionForRemovedCard 只移除被移除卡并保留其他卡继续', () => {
  const session = {
    mode: 'random',
    queueItems: [
      makeItem(1),
      makeItem(2),
      makeRetryItem(1, 'a2b', 0, 1),
      makeItem(3),
    ],
    current: 1,
    retryQueueItems: [
      makeRetryItem(1, 'a2b', 0, 1),
      makeRetryItem(2, 'a2b', 0, 1),
    ],
    postRoundRetryCards: [{ cardId: 1 }, { cardId: 2 }],
    masteredQueue: [{ id: 1 }, { id: 2 }],
    firstRatedIds: ['1:a2b', '2:a2b'],
    cardProgressState: {
      requiredDirectionsByCard: { 1: ['a2b'], 2: ['a2b'] },
      completedDirectionsByCard: { 1: ['a2b'], 2: ['a2b'] },
    },
    distributionState: {
      cards: [
        { cardId: 1, directions: {} },
        { cardId: 2, directions: {} },
      ],
    },
    history: [{ cardId: 1 }, { cardId: 2 }],
  }

  const result = pruneSavedSessionForRemovedCard(session, 1)

  assert.deepEqual(result.queueItems.map(item => item.cardId), [2, 3])
  assert.deepEqual(result.retryQueueItems.map(item => item.cardId), [2])
  assert.deepEqual(result.postRoundRetryCards.map(item => item.cardId), [2])
  assert.deepEqual(result.masteredQueue.map(item => item.id), [2])
  assert.deepEqual(result.firstRatedIds, ['2:a2b'])
  assert.deepEqual(result.cardProgressState.requiredDirectionsByCard, { 2: ['a2b'] })
  assert.deepEqual(result.cardProgressState.completedDirectionsByCard, { 2: ['a2b'] })
  assert.deepEqual(result.distributionState.cards.map(card => card.cardId), [2])
  assert.deepEqual(result.history, [])
  assert.equal(result.current, 0)
})

test('pruneSavedSessionForRemovedCard 被移除卡删除后无剩余队列则返回null', () => {
  const session = {
    mode: 'random',
    queueItems: [makeItem(1), makeRetryItem(1, 'a2b', 0, 1)],
    current: 0,
    retryQueueItems: [],
    masteredQueue: [],
    practiceFinished: false,
  }

  const result = pruneSavedSessionForRemovedCard(session, 1)

  assert.equal(result, null)
})

test('pruneSavedSessionForRemovedCards 一次移除多张卡并保留剩余卡继续', () => {
  const session = {
    mode: 'random',
    queueItems: [makeItem(1), makeItem(2), makeItem(3), makeItem(4)],
    current: 3,
    retryQueueItems: [makeRetryItem(1, 'a2b', 0, 1), makeRetryItem(4, 'a2b', 0, 1)],
    firstRatedIds: ['1:a2b', '2:a2b', '4:a2b'],
    cardProgressState: {
      requiredDirectionsByCard: { 1: ['a2b'], 2: ['a2b'], 4: ['a2b'] },
      completedDirectionsByCard: { 1: ['a2b'], 2: ['a2b'], 4: ['a2b'] },
    },
    distributionState: {
      cards: [
        { cardId: 1, directions: {} },
        { cardId: 2, directions: {} },
        { cardId: 4, directions: {} },
      ],
    },
  }

  const result = pruneSavedSessionForRemovedCards(session, [1, 3])

  assert.deepEqual(result.queueItems.map(item => item.cardId), [2, 4])
  assert.deepEqual(result.retryQueueItems.map(item => item.cardId), [4])
  assert.deepEqual(result.firstRatedIds, ['2:a2b', '4:a2b'])
  assert.deepEqual(result.cardProgressState.requiredDirectionsByCard, { 2: ['a2b'], 4: ['a2b'] })
  assert.deepEqual(result.distributionState.cards.map(card => card.cardId), [2, 4])
  assert.equal(result.current, 1)
})
