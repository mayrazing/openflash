import {
  clonePendingReplay,
  moveItemToNextSlot,
} from './practiceReplay.js'
import { reorderTailAvoidingAdjacentCards } from './queueShuffle.js'

const RATING_RETRY_COUNT = [3, 2, 1, 0] // index = rating value (0=完全不会, 1=模糊, 2=想起来了, 3=记得很清楚)
const MAX_RETRY_COUNT = 3

export function createEmptyPracticeSessionState() {
  return {
    queue: [],
    current: 0,
    stats: createEmptyStats(),
    cardProgress: createEmptyCardProgressState(),
    retryQueueItems: [],
    masteredQueue: [],
    distribution: createEmptyDistributionState(),
    firstRatedIds: [],
    pendingReplay: null,
  }
}

export function createEmptyDistributionState() {
  return { cards: [] }
}

function createEmptyStats() {
  return { again: 0, hard: 0, good: 0, easy: 0, newCount: 0, reviewCountStat: 0, masteredCount: 0 }
}

export function createDistributionDirectionState() {
  return {
    present: false,
    retryActive: false,
    completed: false,
    initialRating: null,
    retryCompleted: [false, false, false],
  }
}

function createDistributionCardState(item) {
  return {
    cardId: item.cardId,
    label: item.card?.sideA || item.card?.sideB || String(item.cardId),
    directions: {
      a2b: createDistributionDirectionState(),
      b2a: createDistributionDirectionState(),
    },
  }
}

export function cloneDistributionState(distributionState) {
  return {
    cards: (distributionState?.cards ?? []).map(card => ({
      ...card,
      directions: {
        a2b: cloneDistributionDirectionState(card.directions?.a2b),
        b2a: cloneDistributionDirectionState(card.directions?.b2a),
      },
    })),
  }
}

function cloneDistributionDirectionState(directionState) {
  return {
    ...createDistributionDirectionState(),
    ...(directionState ?? {}),
    retryCompleted: [...(directionState?.retryCompleted ?? [false, false, false])],
  }
}

export function ratingsForPracticeItem(ratings, distributionState, item) {
  const availableRatings = ratings ?? []
  if (!item?.cardId || item.isRepractice) return availableRatings

  const card = (distributionState?.cards ?? []).find(entry => String(entry.cardId) === String(item.cardId))
  const oppositeDirection = item.direction === 'b2a' ? 'a2b' : 'b2a'
  const oppositeRating = card?.directions?.[oppositeDirection]?.initialRating
  if (oppositeRating !== 0 && oppositeRating !== 1) return availableRatings

  return availableRatings.filter(rating => rating.value !== 3)
}

function ensureDistributionCard(distributionState, item) {
  const cardId = String(item.cardId)
  let card = distributionState.cards.find(entry => String(entry.cardId) === cardId)
  if (!card) {
    card = createDistributionCardState(item)
    distributionState.cards.push(card)
  }
  return card
}

export function buildDistributionState(items) {
  const nextState = createEmptyDistributionState()
  for (const item of items ?? []) {
    if (!item?.cardId || item?.replayOnly) continue
    const direction = item.direction === 'b2a' ? 'b2a' : 'a2b'
    const card = ensureDistributionCard(nextState, item)
    const directionState = card.directions[direction]
    directionState.present = true
    if (item.isRepractice) directionState.retryActive = true
  }
  return nextState
}

export function syncDistributionWithQueue(distributionState, items) {
  const nextState = cloneDistributionState(distributionState)
  for (const item of items ?? []) {
    if (!item?.cardId || item?.replayOnly) continue
    const direction = item.direction === 'b2a' ? 'b2a' : 'a2b'
    const card = ensureDistributionCard(nextState, item)
    const directionState = card.directions[direction]
    directionState.present = true
    if (item.isRepractice) directionState.retryActive = true
  }
  return nextState
}

function updateDistributionDirection(distributionState, item, updater) {
  if (!item?.cardId) return cloneDistributionState(distributionState)

  const nextState = cloneDistributionState(distributionState)
  const direction = item.direction === 'b2a' ? 'b2a' : 'a2b'
  const card = ensureDistributionCard(nextState, item)
  const directionState = card.directions[direction]
  directionState.present = true
  updater(directionState)
  return nextState
}

export function markDistributionBaseResult(distributionState, item, passed, retryCount = 3, initialRating = null) {
  return updateDistributionDirection(distributionState, item, directionState => {
    directionState.initialRating = initialRating
    if (passed) {
      directionState.completed = true
      return
    }

    directionState.completed = false
    directionState.retryActive = true
    directionState.retryCompleted = Array(retryCount).fill(false)
  })
}

// 取得当前重练次数，旧断点缺字段时沿用分布图已有槽数。
function resolveDistributionRetryCount(item, directionState) {
  return item.retryCount ?? directionState.retryCompleted?.length ?? 3
}

// 分子为零时增加重练目标次数，最多不超过三次。
function incrementRetryCount(retryCount) {
  return Math.min(MAX_RETRY_COUNT, retryCount + 1)
}

// 重练失败先扣除一次已完成进度；没有已完成进度时才增加目标次数。
function resolveRetryFailureState(item, directionState) {
  const currentRetryCount = Math.min(
    MAX_RETRY_COUNT,
    Math.max(1, resolveDistributionRetryCount(item, directionState))
  )
  const currentProgress = (directionState?.retryCompleted ?? []).filter(Boolean).length
  return {
    retryCount: currentProgress > 0 ? currentRetryCount : incrementRetryCount(currentRetryCount),
    retryProgress: Math.max(0, currentProgress - 1),
  }
}

// 掌握整张卡时保留用户原本点出来的分布格数，只把已有格子补成绿色。
function markDistributionDirectionMastered(directionState, fallbackRetryCount = 1) {
  directionState.present = true
  directionState.completed = true
  if (directionState.retryActive) {
    const retryCount = directionState.retryCompleted?.length ?? fallbackRetryCount
    directionState.retryCompleted = Array(Math.max(1, retryCount)).fill(true)
  }
}

export function markDistributionRetryResult(distributionState, item, passed) {
  return updateDistributionDirection(distributionState, item, directionState => {
    directionState.retryActive = true
    const retryCount = resolveDistributionRetryCount(item, directionState)
    if (!passed) {
      const failureState = resolveRetryFailureState(item, directionState)
      directionState.retryCompleted = Array.from(
        { length: failureState.retryCount },
        (_, index) => index < failureState.retryProgress
      )
      return
    }

    const maxOrdinal = retryCount - 1
    const ordinal = Math.max(0, Math.min(maxOrdinal, item.ordinal ?? 0))
    directionState.retryCompleted = directionState.retryCompleted.map((completed, index) => (
      index === ordinal ? true : completed
    ))
  })
}

export function markDistributionCardMastered(distributionState, item) {
  if (!item?.cardId) return cloneDistributionState(distributionState)

  const nextState = updateDistributionDirection(distributionState, item, () => {})
  const card = ensureDistributionCard(nextState, item)
  for (const directionState of Object.values(card.directions)) {
    markDistributionDirectionMastered(directionState, item.retryCount ?? 1)
  }
  return nextState
}

export function createEmptyCardProgressState() {
  return { requiredDirectionsByCard: {}, completedDirectionsByCard: {} }
}

function cloneDirectionMap(directionMap = {}) {
  return Object.fromEntries(
    Object.entries(directionMap).map(([cardId, directions]) => [cardId, [...(directions ?? [])]])
  )
}

export function cloneCardProgressState(progressState) {
  return {
    requiredDirectionsByCard: cloneDirectionMap(progressState?.requiredDirectionsByCard),
    completedDirectionsByCard: cloneDirectionMap(progressState?.completedDirectionsByCard),
  }
}

function addDirectionToMap(directionMap, cardId, direction) {
  const key = String(cardId)
  const directions = new Set(directionMap[key] ?? [])
  directions.add(direction)
  directionMap[key] = [...directions]
}

export function buildCardProgressState(baseItems, completedIdentities = []) {
  const progressState = createEmptyCardProgressState()
  for (const item of baseItems ?? []) {
    if (!item || item.isRepractice) continue
    addDirectionToMap(progressState.requiredDirectionsByCard, item.cardId, item.direction)
  }
  for (const identity of completedIdentities ?? []) {
    const [cardId, direction] = String(identity).split(':')
    if (!cardId || !direction) continue
    if (!(cardId in progressState.requiredDirectionsByCard)) continue
    addDirectionToMap(progressState.completedDirectionsByCard, cardId, direction)
  }
  return progressState
}

export function markDirectionCompleted(progressState, cardId, direction) {
  const nextState = cloneCardProgressState(progressState)
  if (String(cardId) in nextState.requiredDirectionsByCard) {
    addDirectionToMap(nextState.completedDirectionsByCard, cardId, direction)
  }
  return nextState
}

export function markCardCompleted(progressState, cardId) {
  const nextState = cloneCardProgressState(progressState)
  const key = String(cardId)
  if (key in nextState.requiredDirectionsByCard) {
    nextState.completedDirectionsByCard[key] = [...nextState.requiredDirectionsByCard[key]]
  }
  return nextState
}

export function countCompletedCards(progressState) {
  return Object.entries(progressState?.requiredDirectionsByCard ?? {}).filter(([cardId, requiredDirections]) => {
    const completedDirections = new Set(progressState?.completedDirectionsByCard?.[cardId] ?? [])
    return requiredDirections.length > 0 && requiredDirections.every(direction => completedDirections.has(direction))
  }).length
}

export function countTotalCards(progressState) {
  return Object.keys(progressState?.requiredDirectionsByCard ?? {}).length
}

export function buildItemKey(cardId, direction, kind = 'base', ordinal = 0) {
  return `${cardId}:${direction}:${kind}:${ordinal}`
}

export function createRetryItems(item, kind = 'retry', retryCount = 3) {
  return Array.from({ length: retryCount }, (_, ordinal) => ({
    ...item,
    itemKey: buildItemKey(item.cardId, item.direction, kind, ordinal),
    kind,
    ordinal,
    retryCount,
    isRepractice: true,
  }))
}

export function noConsecutiveShuffle(items, prevCardId = null) {
  if (items.length === 0) return []

  return repairAdjacentCards(shuffleItems(items), prevCardId)
}

// 整体洗散队列，保证重练题先随机而不是先按卡片分组。
function shuffleItems(items) {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

// 扫描并交换相邻同卡；没有其他卡可换时，才保留不可避免的相邻。
function repairAdjacentCards(items, prevCardId = null) {
  const result = [...items]
  let lastId = prevCardId
  for (let i = 0; i < result.length; i++) {
    if (sameCardId(result[i].cardId, lastId)) {
      const swapIndex = findNextDifferentCardIndex(result, i + 1, result[i].cardId)
      if (swapIndex !== -1) {
        ;[result[i], result[swapIndex]] = [result[swapIndex], result[i]]
      }
    }
    lastId = result[i].cardId
  }

  return result
}

function sameCardId(leftId, rightId) {
  return leftId !== null
    && leftId !== undefined
    && rightId !== null
    && rightId !== undefined
    && String(leftId) === String(rightId)
}

function hasAdjacentCardConflict(items, prevCardId = null, nextCardId = null) {
  let lastId = prevCardId
  for (const item of items) {
    if (sameCardId(item.cardId, lastId)) return true
    lastId = item.cardId
  }
  return sameCardId(lastId, nextCardId)
}

// 非重练题和重练题分段展示时，必要时调换非重练尾项，避免段落交界处同卡连着出现。
function reorderTailBoundaryAwayFromNextCard(items, nextItem, prevCardId = null) {
  if (!nextItem || items.length < 2 || !hasAdjacentCardConflict(items, prevCardId, nextItem.cardId)) {
    return items
  }

  const lastIndex = items.length - 1
  for (let i = 0; i < lastIndex; i++) {
    const candidate = [...items]
    ;[candidate[i], candidate[lastIndex]] = [candidate[lastIndex], candidate[i]]
    if (!hasAdjacentCardConflict(candidate, prevCardId, nextItem.cardId)) return candidate
  }

  return items
}

export function normalizeQueueWithLockedPrefix(queue, lockedCount = 0) {
  const safeLockedCount = Math.max(0, Math.min(lockedCount, queue.length))
  const locked = queue.slice(0, safeLockedCount)
  const tail = queue.slice(safeLockedCount)
  if (tail.length === 0) return [...locked]

  const prevId = locked.length > 0 ? locked[locked.length - 1].cardId : null
  const nonRetry = tail.filter(item => !item.isRepractice)
  const retry = tail.filter(item => item.isRepractice)
  let arrangedNonRetry = noConsecutiveShuffle(nonRetry, prevId)
  let retryPrevId = arrangedNonRetry.length > 0
    ? arrangedNonRetry[arrangedNonRetry.length - 1].cardId
    : prevId
  let arrangedRetry = noConsecutiveShuffle(retry, retryPrevId)
  arrangedNonRetry = reorderTailBoundaryAwayFromNextCard(arrangedNonRetry, arrangedRetry[0], prevId)
  retryPrevId = arrangedNonRetry.length > 0
    ? arrangedNonRetry[arrangedNonRetry.length - 1].cardId
    : prevId
  arrangedRetry = noConsecutiveShuffle(retry, retryPrevId)

  return [...locked, ...arrangedNonRetry, ...arrangedRetry]
}

export function shuffleRetryTail(queue, currentIndex) {
  return normalizeQueueWithLockedPrefix(queue, currentIndex + 1)
}

export function findNextDifferentCardIndex(queue, startIndex, cardId) {
  for (let i = Math.max(0, startIndex); i < queue.length; i++) {
    if (queue[i].cardId !== cardId) return i
  }
  return -1
}

export function findNextSameCardIndex(queue, startIndex, cardId) {
  for (let i = Math.max(0, startIndex); i < queue.length; i++) {
    if (queue[i].cardId === cardId) return i
  }
  return -1
}

// 旧断点缺retryCount时，用当前队列里同一卡同方向的重练题数量还原次数。
function resolveQueueRetryCount(queue, item) {
  const matchingRetryItems = queue.filter(candidate => (
    candidate.isRepractice
    && candidate.cardId === item.cardId
    && candidate.direction === item.direction
    && candidate.kind === item.kind
  ))
  if (item.retryCount !== null && item.retryCount !== undefined) return item.retryCount
  return matchingRetryItems.length > 0 ? matchingRetryItems.length : 3
}

export function rebuildRetrySet(queue, currentIndex, item, failureState = null) {
  const head = queue.slice(0, currentIndex)
  const tail = queue
    .slice(currentIndex + 1)
    .filter(candidate => !(candidate.isRepractice && candidate.itemKey.startsWith(`${item.cardId}:${item.direction}:`)))
  const nextFailureState = failureState ?? {
    retryCount: incrementRetryCount(resolveQueueRetryCount(queue, item)),
    retryProgress: 0,
  }
  const retryItems = createRetryItems(item, item.kind ?? 'retry', nextFailureState.retryCount)
    .slice(nextFailureState.retryProgress)
  const immediateItem = retryItems[0]
  const deferredItems = retryItems.slice(1)
  const insertPos = tail.findIndex(candidate => candidate.cardId !== item.cardId)
  let newTail

  if (insertPos === -1) {
    newTail = [immediateItem, ...tail, ...deferredItems]
  } else {
    newTail = [
      ...tail.slice(0, insertPos + 1),
      immediateItem,
      ...tail.slice(insertPos + 1),
      ...deferredItems,
    ]
  }

  return [...head, ...newTail]
}

export function dedupeMasteredQueue(items) {
  const unique = new Map()
  for (const item of items ?? []) {
    if (!item?.id || unique.has(item.id)) continue
    unique.set(item.id, item)
  }
  return [...unique.values()]
}

export function practiceIdentity(item) {
  return `${item.cardId}:${item.direction}`
}

function normalizeCardIdSet(cardIds) {
  return new Set((Array.isArray(cardIds) ? cardIds : [cardIds])
    .filter(cardId => cardId !== null && cardId !== undefined)
    .map(cardId => String(cardId)))
}

// 判断练习条目是否属于待移除卡片，兼容队列项和收集提示项两种字段名。
function isSessionItemForCardIds(item, cardIds) {
  if (!item) return false
  const itemCardId = item.cardId ?? item.id
  return cardIds.has(String(itemCardId))
}

// 移除方向进度里待移除卡片的记录。
function pruneDirectionMap(directionMap, cardIds) {
  const nextMap = { ...(directionMap ?? {}) }
  for (const cardId of cardIds) {
    delete nextMap[String(cardId)]
  }
  return nextMap
}

// 卡片被重置或删除后，剪掉保存断点里的旧卡片现场，保留其他卡继续。
export function pruneSavedSessionForRemovedCard(session, cardId) {
  return pruneSavedSessionForRemovedCards(session, [cardId])
}

// 多张卡片被删除后，一次性剪掉保存断点里的旧卡片现场。
export function pruneSavedSessionForRemovedCards(session, cardIds) {
  if (!session?.mode) return null
  const removedCardIds = normalizeCardIdSet(cardIds)
  if (removedCardIds.size === 0) return session

  const oldQueueItems = Array.isArray(session.queueItems) ? session.queueItems : []
  const oldCurrent = Math.min(Math.max(session.current ?? 0, 0), Math.max(oldQueueItems.length - 1, 0))
  let removedBeforeCurrent = 0
  const queueItems = oldQueueItems.filter((item, index) => {
    const shouldRemove = isSessionItemForCardIds(item, removedCardIds)
    if (shouldRemove && index < oldCurrent) removedBeforeCurrent += 1
    return !shouldRemove
  })
  const retryQueueItems = (session.retryQueueItems ?? []).filter(item => !isSessionItemForCardIds(item, removedCardIds))
  const postRoundRetryCards = (session.postRoundRetryCards ?? []).filter(item => !isSessionItemForCardIds(item, removedCardIds))
  const masteredQueue = (session.masteredQueue ?? []).filter(item => !isSessionItemForCardIds(item, removedCardIds))

  if (queueItems.length === 0 && retryQueueItems.length === 0 && postRoundRetryCards.length === 0 && masteredQueue.length === 0) {
    return null
  }

  const current = queueItems.length === 0
    ? 0
    : Math.min(Math.max(oldCurrent - removedBeforeCurrent, 0), queueItems.length - 1)

  return {
    ...session,
    queueItems,
    current,
    revealed: false,
    retryQueueItems,
    postRoundRetryCards,
    masteredQueue,
    history: [],
    pendingReplay: null,
    firstRatedIds: (session.firstRatedIds ?? []).filter(identity => !removedCardIds.has(String(identity).split(':')[0])),
    cardProgressState: {
      requiredDirectionsByCard: pruneDirectionMap(session.cardProgressState?.requiredDirectionsByCard, removedCardIds),
      completedDirectionsByCard: pruneDirectionMap(session.cardProgressState?.completedDirectionsByCard, removedCardIds),
    },
    distributionState: {
      ...(session.distributionState ?? {}),
      cards: (session.distributionState?.cards ?? []).filter(card => !removedCardIds.has(String(card.cardId))),
    },
    practiceFinished: Boolean(session.practiceFinished) && queueItems.length === 0,
    savedAt: Date.now(),
  }
}

function cloneStats(stats) {
  return {
    ...createEmptyStats(),
    ...(stats ?? {}),
  }
}

function cloneQueue(queue) {
  return (queue ?? []).map(item => ({ ...item }))
}

function cloneMasteredQueue(masteredQueue) {
  return (masteredQueue ?? []).map(item => ({ ...item }))
}

function cloneRetryQueueItems(retryQueueItems) {
  return (retryQueueItems ?? []).map(item => ({ ...item }))
}

function clonePracticeSessionState(state) {
  return {
    queue: cloneQueue(state?.queue),
    current: state?.current ?? 0,
    stats: cloneStats(state?.stats),
    cardProgress: cloneCardProgressState(state?.cardProgress),
    retryQueueItems: cloneRetryQueueItems(state?.retryQueueItems),
    masteredQueue: cloneMasteredQueue(state?.masteredQueue),
    distribution: cloneDistributionState(state?.distribution),
    firstRatedIds: [...(state?.firstRatedIds ?? [])],
    pendingReplay: clonePendingReplay(state?.pendingReplay),
  }
}

function snapshotRetryQueueItems(queue, currentIndex) {
  return queue
    .slice(currentIndex + 1)
    .filter(item => item.isRepractice && !item.replayOnly)
    .map(item => ({
      itemKey: item.itemKey,
      cardId: item.cardId,
      direction: item.direction,
      kind: item.kind,
      ordinal: item.ordinal,
      isNew: !!item.isNew,
      isReview: !!item.isReview,
      retryCount: item.retryCount ?? 3,
    }))
}

function hasSameCardId(left, right) {
  return sameCardId(left?.cardId, right?.cardId)
}

function canInsertItemWithoutAdjacentCard(queue, index, item) {
  return !hasSameCardId(queue[index - 1], item) && !hasSameCardId(queue[index], item)
}

function findRequeueInsertIndex(queue, currentIndex, item) {
  const preferredIndex = Math.min(currentIndex + 2, queue.length)
  for (let index = preferredIndex; index <= queue.length; index++) {
    if (canInsertItemWithoutAdjacentCard(queue, index, item)) return index
  }
  for (let index = currentIndex + 1; index < preferredIndex; index++) {
    if (canInsertItemWithoutAdjacentCard(queue, index, item)) return index
  }
  return preferredIndex
}

// 将当前题后移，让界面先展示下一题；不改变任何评分状态。
function requeueCurrentItemAfterNext(queue, currentIndex) {
  const nextQueue = [...queue]
  const [requeuedItem] = nextQueue.splice(currentIndex, 1)
  const insertAt = findRequeueInsertIndex(nextQueue, currentIndex, requeuedItem)
  nextQueue.splice(insertAt, 0, requeuedItem)
  return nextQueue
}

/**
 * 完成整张卡后移除本轮后续同卡题目，并推进到下一题。
 */
function finishWholeCard(nextState, item, completedCardId) {
  const reducedQueue = nextState.queue.filter((queueItem, index) => (
    index <= nextState.current || String(queueItem.cardId) !== completedCardId
  ))
  const nextIndex = nextState.current + 1
  const nextFinished = nextIndex >= reducedQueue.length

  return {
    queue: reducedQueue,
    cardProgress: markCardCompleted(nextState.cardProgress, item.cardId),
    distribution: markDistributionCardMastered(nextState.distribution, item),
    retryQueueItems: snapshotRetryQueueItems(reducedQueue, nextState.current),
    pendingReplay: null,
    current: nextFinished ? nextState.current : nextIndex,
    nextIndex,
    nextFinished,
  }
}

/**
 * 慢想起处理：当前重练题本次不计成功/失败，只后移并先展示下一题。
 */
export function applyRetrySlowRecall(state) {
  const nextState = clonePracticeSessionState(state)
  const queue = requeueCurrentItemAfterNext(nextState.queue, nextState.current)
  const nextIndex = nextState.current
  const nextFinished = nextIndex >= queue.length
  return {
    ...nextState,
    queue,
    retryQueueItems: snapshotRetryQueueItems(queue, nextState.current),
    pendingReplay: null,
    current: nextFinished ? nextState.current : nextIndex,
    nextIndex,
    nextFinished,
  }
}

export function applyRating(state, item, rating, backendResult = {}, selectedRating = rating) {
  const nextState = clonePracticeSessionState(state)
  const identity = practiceIdentity(item)
  const wasFirstRated = !nextState.firstRatedIds.includes(identity)

  if (wasFirstRated) {
    nextState.firstRatedIds = [...nextState.firstRatedIds, identity]
    const statKey = ['again', 'hard', 'good', 'easy'][rating]
    if (statKey) nextState.stats[statKey] += 1
    nextState.cardProgress = markDirectionCompleted(nextState.cardProgress, item.cardId, item.direction)
  }

  const refreshedCard = backendResult?.refreshedCard ?? backendResult?.card ?? item.card
  if (wasFirstRated) {
    nextState.queue = nextState.queue.map(queueItem => (
      queueItem.cardId === item.cardId ? { ...queueItem, card: refreshedCard } : queueItem
    ))

    nextState.masteredQueue = nextState.masteredQueue.filter(entry => entry.id !== item.cardId)
  }

  if (backendResult?.graduated === true) {
    const graduatedCardId = String(item.cardId)

    return {
      ...nextState,
      ...finishWholeCard(nextState, item, graduatedCardId),
    }
  }

  const retryCount = RATING_RETRY_COUNT[rating] ?? 0
  if (retryCount > 0) {
    nextState.distribution = markDistributionBaseResult(nextState.distribution, item, false, retryCount, selectedRating)
    const retryItems = createRetryItems(item, 'retry', retryCount)
    nextState.queue = shuffleRetryTail([...nextState.queue, ...retryItems], nextState.current)
    nextState.retryQueueItems = snapshotRetryQueueItems(nextState.queue, nextState.current)
  } else {
    nextState.distribution = markDistributionBaseResult(nextState.distribution, item, true, retryCount, selectedRating)
    nextState.retryQueueItems = nextState.retryQueueItems.filter(savedItem => savedItem.cardId !== item.cardId)
  }

  const activeReplay = nextState.pendingReplay?.sourceItemKey === item.itemKey
    ? clonePendingReplay(nextState.pendingReplay)
    : null
  let nextPendingReplay = nextState.pendingReplay && !activeReplay ? null : nextState.pendingReplay
  let nextIndex = nextState.current + 1
  let nextFinished = nextIndex >= nextState.queue.length

  if (activeReplay) {
    nextPendingReplay = null
    const replayResult = moveItemToNextSlot(nextState.queue, nextState.current, activeReplay.target.itemKey)
    if (replayResult.found) {
      nextState.queue = replayResult.queue
      nextIndex = replayResult.nextIndex
      nextFinished = nextIndex >= nextState.queue.length
      nextState.retryQueueItems = snapshotRetryQueueItems(nextState.queue, nextState.current)
    }
  }

  return {
    ...nextState,
    current: nextFinished ? nextState.current : nextIndex,
    pendingReplay: nextPendingReplay,
    nextIndex,
    nextFinished,
  }
}

export function applyRetry(state, item, passed) {
  const nextState = clonePracticeSessionState(state)

  if (passed) {
    const nextIndex = nextState.current + 1
    const nextFinished = nextIndex >= nextState.queue.length
    return {
      ...nextState,
      distribution: markDistributionRetryResult(nextState.distribution, item, true),
      retryQueueItems: snapshotRetryQueueItems(nextState.queue, nextState.current),
      pendingReplay: null,
      current: nextFinished ? nextState.current : nextIndex,
      nextIndex,
      nextFinished,
    }
  }

  const distributionCard = nextState.distribution.cards.find(card => sameCardId(card.cardId, item.cardId))
  const direction = item.direction === 'b2a' ? 'b2a' : 'a2b'
  const failureState = resolveRetryFailureState(item, distributionCard?.directions?.[direction])
  let finalQueue = rebuildRetrySet(nextState.queue, nextState.current, item, failureState)
  if (finalQueue[nextState.current]?.cardId === item.cardId) {
    const differentIndex = findNextDifferentCardIndex(finalQueue, nextState.current + 1, item.cardId)
    if (differentIndex !== -1) {
      finalQueue = [...finalQueue]
      ;[finalQueue[nextState.current], finalQueue[differentIndex]] = [finalQueue[differentIndex], finalQueue[nextState.current]]
    }
  }

  const immediateRetryIndex = findNextSameCardIndex(finalQueue, nextState.current, item.cardId)
  if (immediateRetryIndex !== -1) {
    finalQueue = reorderTailAvoidingAdjacentCards(finalQueue, immediateRetryIndex + 1)
  }

  const nextFinished = nextState.current >= finalQueue.length
  return {
    ...nextState,
    queue: finalQueue,
    distribution: markDistributionRetryResult(nextState.distribution, item, false),
    retryQueueItems: snapshotRetryQueueItems(finalQueue, nextState.current - 1),
    pendingReplay: null,
    current: nextState.current,
    nextIndex: nextState.current,
    nextFinished,
  }
}

export function applyFamiliar(state, item) {
  const nextState = clonePracticeSessionState(state)
  const reducedQueue = nextState.queue.filter((queueItem, index) => (
    index < nextState.current || queueItem.cardId !== item.cardId
  ))
  const nextIndex = nextState.current
  const nextFinished = nextIndex >= reducedQueue.length

  return {
    ...nextState,
    queue: reducedQueue,
    cardProgress: markCardCompleted(nextState.cardProgress, item.cardId),
    distribution: markDistributionCardMastered(nextState.distribution, item),
    masteredQueue: nextState.masteredQueue.filter(entry => entry.id !== item.cardId),
    stats: { ...nextState.stats, masteredCount: nextState.stats.masteredCount + 1 },
    retryQueueItems: snapshotRetryQueueItems(reducedQueue, nextState.current - 1),
    pendingReplay: null,
    current: nextFinished ? nextState.current : nextIndex,
    nextIndex,
    nextFinished,
  }
}

/**
 * 超时作废处理：将当前卡片（item）移到队列后 2 位，不更新 FSRS 也不计分。
 *
 * 逻辑：
 *   queue = [A, B, C, D]，current = 0（正在显示 A）
 *   → 移除 A，插回 index 2 → [B, C, A, D]
 *   → current 保持 0，现在指向 B
 */
export function applyTimeoutRequeue(state) {
  const nextState = clonePracticeSessionState(state)
  const queue = requeueCurrentItemAfterNext(nextState.queue, nextState.current)
  const nextIndex = nextState.current
  const nextFinished = nextIndex >= queue.length
  return {
    ...nextState,
    queue,
    retryQueueItems: snapshotRetryQueueItems(queue, nextState.current),
    pendingReplay: null,
    current: nextFinished ? nextState.current : nextIndex,
    nextIndex,
    nextFinished,
  }
}
