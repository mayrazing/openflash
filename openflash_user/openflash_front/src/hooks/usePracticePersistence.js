import { useEffect } from 'react'
import { savePracticeSession, clearPracticeSession, getCard as getCardFromDb, getToday } from '../db/database.js'
import { snapshotLockedNextPresentation, clonePendingReplay } from '../lib/practiceReplay.js'
import {
  buildItemKey, buildCardProgressState, buildDistributionState,
  cloneCardProgressState, cloneDistributionState, createEmptyCardProgressState, dedupeMasteredQueue,
} from '../lib/practiceSession.js'
import {
  clampHistoryEntries, createEmptyStats, snapshotPracticeQueueItem,
  serializeHistoryEntry, cloneDirectionProgresses, waitForNextPaint,
} from '../lib/practiceQueue.js'
import { appError } from '../lib/appLog.js'
import { normalizeReviewLoadProfile } from '../lib/practiceSessionSettings.js'

function cloneRestoredCard(card) {
  if (!card) return null
  return {
    ...card,
    directionProgresses: cloneDirectionProgresses(card.directionProgresses),
  }
}

async function resolveQueueItemCard(item, options = {}) {
  const { cardCache = new Map(), getCard = getCardFromDb } = options
  const cardId = item.cardId ?? item.id
  if (item.card) return cloneRestoredCard(item.card)

  const cacheKey = String(cardId)
  let cardPromise = cardCache.get(cacheKey)
  if (!cardPromise) {
    cardPromise = getCard(cardId)
    cardCache.set(cacheKey, cardPromise)
  }
  return cloneRestoredCard(await cardPromise)
}

export async function restorePracticeQueueItems(items, options = {}) {
  return (await Promise.all(
    (items ?? []).map(async item => {
      const cardId = item.cardId ?? item.id
      const fullCard = await resolveQueueItemCard(item, options)
      if (!fullCard) return null
      return {
        itemKey: item.itemKey ?? buildItemKey(cardId, item.direction ?? 'a2b', item.kind ?? 'base', item.ordinal ?? 0),
        cardId,
        direction: item.direction ?? (item.questionSide === 'b' ? 'b2a' : 'a2b'),
        kind: item.kind ?? (item.isRepractice ? 'retry' : 'base'),
        ordinal: item.ordinal ?? 0,
        card: fullCard,
        isNew: !!item.isNew,
        isReview: item.isReview ?? !item.isNew,
        isRepractice: !!item.isRepractice,
        replayOnly: !!item.replayOnly,
        retryCount: item.retryCount,
      }
    })
  )).filter(Boolean)
}

export async function restorePracticeHistoryEntries(entries, options = {}) {
  return clampHistoryEntries(await Promise.all(
    (entries ?? []).map(async entry => ({
      queue: await restorePracticeQueueItems(entry.queueItems, options),
      current: entry.current ?? 0,
      stats: { ...createEmptyStats(), ...(entry.stats ?? {}) },
      cardProgressState: entry.cardProgressState
        ? cloneCardProgressState(entry.cardProgressState)
        : createEmptyCardProgressState(),
      masteredQueue: dedupeMasteredQueue(Array.isArray(entry.masteredQueue) ? entry.masteredQueue : []),
      retryQueueItems: [...(entry.retryQueueItems ?? [])],
      distributionState: entry.distributionState ? cloneDistributionState(entry.distributionState) : null,
      cardId: entry.cardId ?? null,
      direction: entry.direction ?? null,
      prevProgress: entry.prevProgress ?? null,
      addedToFirstRated: Boolean(entry.addedToFirstRated),
      cardModifiedInDb: Boolean(entry.cardModifiedInDb),
      revealed: Boolean(entry.revealed),
      lockedNextPresentation: snapshotLockedNextPresentation(entry.lockedNextPresentation),
    }))
  ))
}

export function usePracticePersistence(ctx) {
  const {
    id, t, sessionReady, queue, current, revealed, practiceFinished,
    modeRef, queueRef, currentRef, revealedRef, practiceFinishedRef, masteredQueueRef,
    retryQueueItemsRef, historyRef, pendingReplayRef, sessionDateRef, practiceDayRolloverRef, statsRef,
    cardProgressRef, distributionRef, firstRatedToday, settingsRef,
    sessionSaveChainRef, sessionSaveVersionRef, sessionMutationChainRef,
    setMode, setQueue, setCurrent, setRevealed, setPracticeFinished, setMasteredQueue,
    setCanGoBack, resetHistory, setRetryQueueItems, setPendingReplay, replaceCardProgress,
    replaceDistributionState,
  } = ctx

  function enqueuePracticeSessionSave(sessionPayload) {
    const saveVersion = sessionSaveVersionRef.current + 1
    sessionSaveVersionRef.current = saveVersion
    sessionSaveChainRef.current = sessionSaveChainRef.current
      .catch(() => {})
      .then(async () => {
        await waitForNextPaint()
        if (saveVersion !== sessionSaveVersionRef.current) return
        await savePracticeSession(id, sessionPayload)
      })
      .catch(error => {
        appError(error?.code ?? 50000, t('practice.saveError'), error)
      })
    return sessionSaveChainRef.current
  }

  async function saveCurrentPracticeSession(next = {}, options = {}) {
    const sessionMode = next.mode ?? modeRef.current
    const sessionQueue = next.queue ?? queueRef.current
    const sessionMasteredQueue = dedupeMasteredQueue(next.masteredQueue ?? masteredQueueRef.current)
    const sessionPracticeFinished = next.practiceFinished ?? practiceFinishedRef.current
    const sessionCurrent = next.current ?? currentRef.current
    const sessionRevealed = next.revealed ?? revealedRef.current
    const sessionRetryQueueItems = next.retryQueueItems ?? retryQueueItemsRef.current
    const sessionHistory = clampHistoryEntries(next.history ?? historyRef.current)
    const sessionPendingReplay = Object.prototype.hasOwnProperty.call(next, 'pendingReplay')
      ? clonePendingReplay(next.pendingReplay)
      : clonePendingReplay(pendingReplayRef.current)
    if (!sessionMode) return
    if (sessionQueue.length === 0) return

    queueRef.current = sessionQueue
    currentRef.current = sessionCurrent
    revealedRef.current = sessionRevealed
    practiceFinishedRef.current = sessionPracticeFinished
    masteredQueueRef.current = sessionMasteredQueue
    retryQueueItemsRef.current = sessionRetryQueueItems
    historyRef.current = sessionHistory
    pendingReplayRef.current = sessionPendingReplay
    sessionDateRef.current = next.sessionDate ?? sessionDateRef.current ?? getToday()
    setCanGoBack(sessionHistory.length > 0)

    const sessionPayload = {
      mode: sessionMode,
      queueItems: sessionQueue.map(snapshotPracticeQueueItem),
      current: sessionCurrent,
      revealed: sessionRevealed,
      practiceFinished: sessionPracticeFinished,
      masteredQueue: sessionMasteredQueue,
      postRoundRetryActive: false,
      stats: next.stats ?? statsRef.current,
      firstRatedIds: next.firstRatedIds ?? Array.from(firstRatedToday.current),
      cardProgressState: cloneCardProgressState(next.cardProgressState ?? cardProgressRef.current),
      retryQueueItems: sessionRetryQueueItems,
      postRoundRetryCards: [],
      history: sessionHistory.map(serializeHistoryEntry),
      pendingReplay: sessionPendingReplay,
      distributionState: cloneDistributionState(next.distributionState ?? distributionRef.current),
      sessionSchemaVersion: 2,
      sessionDate: sessionDateRef.current,
      settingsNewCardsPerDay: next.settingsNewCardsPerDay ?? settingsRef.current.newCardsPerDay,
      settingsReviewLoadProfile: normalizeReviewLoadProfile(
        next.settingsReviewLoadProfile ?? settingsRef.current.reviewLoadProfile
      ),
      savedAt: Date.now(),
    }

    const savePromise = enqueuePracticeSessionSave(sessionPayload)
    if (options.flush) await savePromise
  }

  async function runPracticeMutation(fn, options = {}) {
    const { nested = false } = options
    if (practiceDayRolloverRef.current && !options.allowDuringDayRollover) return Promise.resolve()
    if (nested) {
      return fn()
    }
    const run = sessionMutationChainRef.current.catch(() => { }).then(async () => {
      return await fn()
    })
    sessionMutationChainRef.current = run.catch(() => { })
    return run
  }

  async function restoreQueueItems(items, cardCache = new Map()) {
    return restorePracticeQueueItems(items, { cardCache })
  }

  async function restoreHistoryEntries(entries, cardCache = new Map()) {
    return restorePracticeHistoryEntries(entries, { cardCache })
  }

  async function restoreSavedPracticeSession(session) {
    const cardCache = new Map()
    const restoredPrimaryQueue = await restoreQueueItems(session.queueItems, cardCache)
    const restoredRetryQueue = restoredPrimaryQueue.length === 0
      ? await restoreQueueItems(session.retryQueueItems, cardCache)
      : []
    const restoredQueue = restoredPrimaryQueue.length > 0 ? restoredPrimaryQueue : restoredRetryQueue

    const restoredMasteredQueue = dedupeMasteredQueue(Array.isArray(session.masteredQueue) ? session.masteredQueue : [])
    if (restoredQueue.length === 0 && !(session.practiceFinished && restoredMasteredQueue.length > 0)) {
      await clearPracticeSession(id)
      return false
    }

    const restoredCurrent = restoredQueue.length === 0 ? 0 : Math.min(session.current ?? 0, restoredQueue.length - 1)

    const restoredHistory = await restoreHistoryEntries(session.history, cardCache)
    resetHistory(restoredHistory)
    setRetryQueueItems(session.retryQueueItems ?? [])
    setPendingReplay(session.pendingReplay ?? null)
    firstRatedToday.current = new Set(session.firstRatedIds ?? [])
    statsRef.current = { ...createEmptyStats(), ...(session.stats ?? {}) }
    const restoredCardProgressState = session.cardProgressState
      ? cloneCardProgressState(session.cardProgressState)
      : buildCardProgressState(restoredQueue.filter(item => !item.isRepractice), session.firstRatedIds ?? [])
    replaceCardProgress(restoredCardProgressState)
    replaceDistributionState(session.distributionState ?? buildDistributionState(restoredQueue))
    sessionDateRef.current = session.sessionDate ?? getToday()
    modeRef.current = session.mode
    queueRef.current = restoredQueue
    currentRef.current = restoredCurrent
    revealedRef.current = Boolean(session.revealed) && restoredQueue.length > 0
    practiceFinishedRef.current = Boolean(session.practiceFinished)
    masteredQueueRef.current = restoredMasteredQueue
    setMode(session.mode)
    setQueue(restoredQueue)
    setCurrent(restoredCurrent)
    setRevealed(Boolean(session.revealed) && restoredQueue.length > 0)
    setPracticeFinished(Boolean(session.practiceFinished))
    setMasteredQueue(restoredMasteredQueue)
    return true
  }

  function persistRetryQueue(currentQueue, currentIdx) {
    const pendingItems = currentQueue
      .slice(currentIdx + 1)
      .filter(item => item.isRepractice && !item.replayOnly)
      .map(item => ({
        itemKey: item.itemKey,
        cardId: item.cardId,
        direction: item.direction,
        kind: item.kind,
        ordinal: item.ordinal,
        isNew: !!item.isNew,
        isReview: !!item.isReview,
        retryCount: item.retryCount,
      }))
    setRetryQueueItems(pendingItems)
    return pendingItems
  }

  function updateLatestHistoryLock(nextItem) {
    if (historyRef.current.length === 0) return
    historyRef.current = historyRef.current.map((entry, index) => (
      index === historyRef.current.length - 1
        ? { ...entry, lockedNextPresentation: snapshotLockedNextPresentation(nextItem) }
        : entry
    ))
  }

  // 自动存档 effect
  useEffect(() => {
    if (!sessionReady || !modeRef.current || queue.length === 0) return
    if (practiceFinished) return
    runPracticeMutation(() => saveCurrentPracticeSession())
  }, [sessionReady, queue, current, revealed, practiceFinished]) // eslint-disable-line react-hooks/exhaustive-deps

  return {
    enqueuePracticeSessionSave, saveCurrentPracticeSession, runPracticeMutation,
    restoreSavedPracticeSession, restoreQueueItems, restoreHistoryEntries,
    persistRetryQueue, updateLatestHistoryLock,
  }
}
