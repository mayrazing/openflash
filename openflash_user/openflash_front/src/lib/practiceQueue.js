import {
  buildItemKey,
  cloneCardProgressState,
  cloneDistributionState,
} from './practiceSession.js'
import { snapshotLockedNextPresentation } from './practiceReplay.js'

export const DEFAULT_MODES = [
  { value: 'a2b', label: 'A → B' },
  { value: 'b2a', label: 'B → A' },
  { value: 'random', label: 'Random' },
]
export const TODAY_REPRACTICE_MODE = 'todayRepractice'

// 根据模式值显示用户能看懂的名称，后端配置缺失时回退到前端默认名。
export function practiceModeLabel(mode, t, modes = DEFAULT_MODES) {
  if (mode === 'a2b') return t('practice.modeA2B')
  if (mode === 'b2a') return t('practice.modeB2A')
  if (mode === TODAY_REPRACTICE_MODE) return t('practice.todayRepractice')
  if (mode === 'random') return t('practice.randomMode')
  return modes.find(m => m.value === mode)?.label
    ?? DEFAULT_MODES.find(m => m.value === mode)?.label
    ?? mode
}

// 判断旧断点是否因为练习模式已禁用而无法恢复。
export function isDisabledPracticeModeError(error) {
  return error instanceof Error && error.code === 40061 // PRACTICE_MODE_INVALID
}

export const HISTORY_LIMIT = 20

export function createRatings(t) {
  return [
    { value: 0, label: t('practice.ratingAgain'), hint: t('practice.ratingAgainHint'), color: 'bg-app-practice-again-tonal text-app-practice-again active:bg-app-practice-again-pressed' },
    { value: 1, label: t('practice.ratingHard'), hint: t('practice.ratingHardHint'), color: 'bg-app-practice-hard-tonal text-app-practice-hard active:bg-app-practice-hard-pressed' },
    { value: 2, label: t('practice.ratingGood'), hint: t('practice.ratingGoodHint'), color: 'bg-app-practice-good-tonal text-app-practice-good active:bg-app-practice-good-pressed' },
    { value: 3, label: t('practice.ratingEasy'), hint: t('practice.ratingEasyHint'), color: 'bg-app-practice-easy-tonal text-app-practice-easy active:bg-app-practice-easy-pressed' },
  ]
}

export function createEmptyStats() {
  return { again: 0, hard: 0, good: 0, easy: 0, newCount: 0, reviewCountStat: 0, masteredCount: 0 }
}

export function waitForNextPaint() {
  if (typeof window === 'undefined' || typeof window.requestAnimationFrame !== 'function') {
    return Promise.resolve()
  }
  return new Promise(resolve => {
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(resolve)
    })
  })
}

// ── 工具函数 ──────────────────────────────────────────────────

export function countUniqueCards(items) {
  return new Set((items ?? []).map(item => item?.cardId).filter(Boolean)).size
}

export function cloneDirectionProgresses(directionProgresses) {
  if (!directionProgresses) return null
  return {
    a2b: directionProgresses.a2b
      ? {
        ...directionProgresses.a2b,
        fsrs: directionProgresses.a2b.fsrs ? { ...directionProgresses.a2b.fsrs } : null,
      }
      : null,
    b2a: directionProgresses.b2a
      ? {
        ...directionProgresses.b2a,
        fsrs: directionProgresses.b2a.fsrs ? { ...directionProgresses.b2a.fsrs } : null,
      }
      : null,
  }
}

export function hasCompleteDirectionProgresses(card) {
  return Boolean(card?.directionProgresses?.a2b && card?.directionProgresses?.b2a)
}

export function questionSideFromDirection(direction) {
  return direction === 'a2b' ? 'a' : 'b'
}

export function answerSideFromDirection(direction) {
  return direction === 'a2b' ? 'b' : 'a'
}

function itemIdentity(item) {
  return `${item.cardId}:${item.direction}`
}

export function itemStableKey(item) {
  return item.itemKey ?? buildItemKey(item.cardId, item.direction ?? 'a2b', item.kind ?? 'base', item.ordinal ?? 0)
}

export function isBaseNewItem(item) {
  return !!item?.isNew && !item.isRepractice
}

export function countBaseNewCards(items) {
  return new Set((items ?? []).filter(isBaseNewItem).map(item => String(item.cardId))).size
}

export function snapshotPracticeQueueItem(item) {
  return {
    itemKey: item.itemKey,
    cardId: item.cardId,
    direction: item.direction,
    kind: item.kind,
    ordinal: item.ordinal ?? 0,
    isNew: !!item.isNew,
    isReview: !!item.isReview,
    isRepractice: !!item.isRepractice,
    replayOnly: !!item?.replayOnly,
    retryCount: item.retryCount,
  }
}

export function clampHistoryEntries(entries) {
  return (entries ?? []).slice(-HISTORY_LIMIT)
}

function snapshotPersistedCard(card) {
  if (!card) return null
  return {
    ...card,
    directionProgresses: cloneDirectionProgresses(card.directionProgresses),
  }
}

function shouldPersistHistoryItemCard(entry, item, index) {
  const currentItem = entry?.queue?.[entry.current]
  if (currentItem?.itemKey && item?.itemKey) {
    return currentItem.itemKey === item.itemKey
  }
  if (entry?.prevProgress?.itemKey && item?.itemKey) {
    return entry.prevProgress.itemKey === item.itemKey
  }
  if (typeof entry?.current === 'number') {
    return index === entry.current
  }
  return item?.cardId === entry?.cardId
}

export function serializeHistoryEntry(entry) {
  return {
    queueItems: (entry.queue ?? []).map((item, index) => ({
      ...snapshotPracticeQueueItem(item),
      card: shouldPersistHistoryItemCard(entry, item, index)
        ? snapshotPersistedCard(item?.card)
        : null,
    })),
    current: entry.current ?? 0,
    stats: { ...(entry.stats ?? {}) },
    cardProgressState: cloneCardProgressState(entry.cardProgressState),
    masteredQueue: [...(entry.masteredQueue ?? [])],
    retryQueueItems: [...(entry.retryQueueItems ?? [])],
    distributionState: entry.distributionState ? cloneDistributionState(entry.distributionState) : null,
    cardId: entry.cardId ?? null,
    direction: entry.direction ?? null,
    prevProgress: entry.prevProgress ?? null,
    addedToFirstRated: Boolean(entry.addedToFirstRated),
    cardModifiedInDb: Boolean(entry.cardModifiedInDb),
    revealed: Boolean(entry.revealed),
    lockedNextPresentation: snapshotLockedNextPresentation(entry.lockedNextPresentation),
  }
}

export function hasSavedSessionContinuation(session) {
  if (!session?.mode) return false
  const hasQueue = Array.isArray(session.queueItems) && session.queueItems.length > 0
  const hasRetryQueue = Array.isArray(session.retryQueueItems) && session.retryQueueItems.length > 0
  return hasQueue || hasRetryQueue
}

export function normalizePracticeSessionMode(session) {
  if (!session || session.mode === TODAY_REPRACTICE_MODE) return session
  const queueItems = [
    ...(session.queueItems ?? []),
    ...(session.retryQueueItems ?? []),
  ]
  const isLegacyTodayRepractice = queueItems.length > 0
    && queueItems.every(item => item?.isRepractice && item?.kind === 'todayRepractice')
  return isLegacyTodayRepractice ? { ...session, mode: TODAY_REPRACTICE_MODE } : session
}

export function isTodayRepracticeSessionStillValid(session, pendingSummary, todayCards) {
  if (session?.mode !== TODAY_REPRACTICE_MODE) return true
  if ((pendingSummary?.pendingTotal ?? 0) !== 0) return false
  if (!Array.isArray(todayCards) || todayCards.length === 0) return false

  const todayCardIds = new Set(todayCards.map(card => String(card.id)))
  const sessionCardIds = new Set(
    [
      ...(session.queueItems ?? []),
      ...(session.retryQueueItems ?? []),
    ].map(item => String(item.cardId ?? item.id)).filter(Boolean)
  )
  if (sessionCardIds.size === 0) return false
  return [...sessionCardIds].every(cardId => todayCardIds.has(cardId))
}

function shuffleArray(items) {
  const next = [...items]
  for (let i = next.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
      ;[next[i], next[j]] = [next[j], next[i]]
  }
  return next
}

// 基于当前 session 和 fresh queue 结果，按 cardId 生成可追加的新卡分组。
function buildAppendCardGroups(existingQueue, candidateQueue, delta) {
  if (delta <= 0) return []

  const existingCardIds = new Set((existingQueue ?? []).map(item => String(item.cardId)).filter(Boolean))
  const groups = []
  const groupsByCardId = new Map()

  for (const item of candidateQueue ?? []) {
    if (!isBaseNewItem(item)) continue
    const cardId = String(item.cardId)
    if (!cardId || existingCardIds.has(cardId)) continue

    let group = groupsByCardId.get(cardId)
    if (!group) {
      if (groups.length >= delta) continue
      group = []
      groupsByCardId.set(cardId, group)
      groups.push(group)
    }
    group.push(item)
  }

  return groups.filter(group => group.length > 0)
}

export function appendNewCardGroups(existingQueue, candidateQueue, newCardsLimit) {
  const safeLimit = Math.max(0, Number(newCardsLimit) || 0)
  const existingUniqueNewCards = countBaseNewCards(existingQueue)
  const delta = Math.max(0, safeLimit - existingUniqueNewCards)
  if (delta === 0) {
    return { queue: existingQueue, appendedCardCount: 0, changed: false }
  }

  const cardGroups = buildAppendCardGroups(existingQueue, candidateQueue, delta)
  if (cardGroups.length === 0) {
    return { queue: existingQueue, appendedCardCount: 0, changed: false }
  }

  const orderedGroups = cardGroups.length >= 3 ? shuffleArray(cardGroups) : cardGroups
  return {
    queue: [...existingQueue, ...orderedGroups.flat()],
    appendedCardCount: cardGroups.length,
    changed: true,
  }
}

// 按新的每日新卡上限裁剪未评分新卡，保留复习、重练和已写入进度的新卡。
export function reconcileQueueForNewLimit(queueItems, currentIndex, revealed, firstRatedIds, newCardsLimit) {
  const safeLimit = Math.max(0, Number(newCardsLimit) || 0)
  const ratedIdentities = new Set(firstRatedIds ?? [])
  const protectedCardIds = new Set()

  for (const item of queueItems ?? []) {
    if (isBaseNewItem(item) && ratedIdentities.has(itemIdentity(item))) {
      protectedCardIds.add(String(item.cardId))
    }
  }

  const keptNewCardIds = new Set(protectedCardIds)
  for (const item of queueItems ?? []) {
    if (!isBaseNewItem(item)) continue
    const cardId = String(item.cardId)
    if (keptNewCardIds.has(cardId)) continue
    if (keptNewCardIds.size >= safeLimit) continue
    keptNewCardIds.add(cardId)
  }

  const oldIndexToNewIndex = new Map()
  const keptQueue = []
  queueItems.forEach((item, index) => {
    const shouldKeep = !isBaseNewItem(item) || keptNewCardIds.has(String(item.cardId))
    if (!shouldKeep) return
    oldIndexToNewIndex.set(index, keptQueue.length)
    keptQueue.push(item)
  })

  const oldCurrentSurvives = oldIndexToNewIndex.has(currentIndex)
  let nextCurrent = oldCurrentSurvives ? oldIndexToNewIndex.get(currentIndex) : 0
  if (!oldCurrentSurvives) {
    const nextOldIndex = queueItems.findIndex((_, index) => index > currentIndex && oldIndexToNewIndex.has(index))
    nextCurrent = nextOldIndex === -1 ? Math.max(0, keptQueue.length - 1) : oldIndexToNewIndex.get(nextOldIndex)
  }

  const oldKeys = (queueItems ?? []).map(itemStableKey)
  const newKeys = keptQueue.map(itemStableKey)
  const changed = oldKeys.length !== newKeys.length ||
    oldKeys.some((key, index) => key !== newKeys[index]) ||
    currentIndex !== nextCurrent ||
    (revealed && !oldCurrentSurvives)

  return {
    changed,
    queue: keptQueue,
    current: keptQueue.length === 0 ? 0 : nextCurrent,
    revealed: oldCurrentSurvives ? revealed : false,
    currentSurvived: oldCurrentSurvives,
  }
}
