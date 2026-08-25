import { useEffect } from 'react'
import { rateCardFsrs, restoreCardFsrs, moveToMastered, clearPracticeSession, getCard } from '../db/database'
import { computeAppliedRating, isTimedOut } from '../lib/practiceResponseTime'
import { playGenericClick, playRatingSound, playRetrySound, stopBgMusic } from '../lib/soundEngine'
import { createPendingReplay, removeCurrentReplayOnly, clonePendingReplay } from '../lib/practiceReplay'
import {
  applyRating as computeApplyRating, applyFamiliar as computeApplyFamiliar,
  applyRetry as computeApplyRetry, applyRetrySlowRecall as computeApplyRetrySlowRecall, applyTimeoutRequeue,
  cloneCardProgressState, practiceIdentity,
  createEmptyCardProgressState, buildDistributionState,
} from '../lib/practiceSession.js'
import {
  questionSideFromDirection, answerSideFromDirection,
  hasCompleteDirectionProgresses, cloneDirectionProgresses,
} from '../lib/practiceQueue'

export function usePracticeEngine(ctx) {
  const {
    id, navigate, mode, queue, current, revealed,
    setQueue, setCurrent, setRevealed, setPracticeFinished, setMasteredQueue, setFamiliarToast,
    masteredQueue, firstRatedToday, statsRef, timingLogRef, revealTimeRef, cardShownTimeRef,
    responseTimeThresholdsRef, queueRef, currentRef, masteredQueueRef, retryQueueItemsRef,
    cardProgressRef, distributionRef, pendingReplayRef, practiceFinished,
    historyRef, modeRef, revealedRef, practiceFinishedRef, scrollRef,
    setCanGoBack, resetSessionContinuationState,
    pushHistoryEntry, clearPendingReplay, setPendingReplay, setRetryQueueItems,
    replaceCardProgress, replaceDistributionState, advanceTo, dispatchPracticeFaceShown,
    runPracticeMutation, saveCurrentPracticeSession, persistRetryQueue, updateLatestHistoryLock,
  } = ctx

  async function consumeReplayOnlyItem(item) {
    pushHistoryEntry({
      queue: [...queue],
      current,
      stats: { ...statsRef.current },
      cardProgressState: cloneCardProgressState(cardProgressRef.current),
      masteredQueue: [...masteredQueue],
      retryQueueItems: [...retryQueueItemsRef.current],
      cardId: item.cardId,
      direction: item.direction,
      prevProgress: null,
      addedToFirstRated: false,
      cardModifiedInDb: false,
      revealed,
    })
    clearPendingReplay()
    const { queue: finalQueue, nextIndex } = removeCurrentReplayOnly(queue, current)
    const nextFinished = nextIndex >= finalQueue.length
    if (!nextFinished) {
      persistRetryQueue(finalQueue, nextIndex - 1)
    } else {
      setRetryQueueItems([])
    }
    updateLatestHistoryLock(!nextFinished ? finalQueue[nextIndex] : null)
    setQueue(finalQueue)
    if (nextFinished) setPracticeFinished(true)
    else {
      setPracticeFinished(false)
      advanceTo(nextIndex)
    }

    await saveCurrentPracticeSession({
      queue: finalQueue,
      current: nextFinished ? current : nextIndex,
      revealed: false,
      practiceFinished: nextFinished,
      stats: statsRef.current,
      firstRatedIds: Array.from(firstRatedToday.current),
      cardProgressState: cardProgressRef.current,
      retryQueueItems: retryQueueItemsRef.current,
      history: historyRef.current,
      pendingReplay: null,
    })
  }

  async function finishPracticeToSummary(options = {}) {
    return runPracticeMutation(async () => {
      resetSessionContinuationState()
      await clearPracticeSession(id)
      navigate(`/deck/${id}/summary`, { state: { stats: statsRef.current, timingLog: timingLogRef.current }, replace: true })
    }, { nested: options.nested })
  }

  async function handleExit() {
    playGenericClick()
    stopBgMusic()
    return runPracticeMutation(async () => {
      if (modeRef.current && queueRef.current.length > 0 && !practiceFinishedRef.current) {
        await saveCurrentPracticeSession({}, { flush: true })
      }
      navigate(`/deck/${id}`)
    })
  }

  useEffect(() => {
    if (!revealed) {
      revealTimeRef.current = null
      cardShownTimeRef.current = Date.now()
    }
  }, [revealed]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!mode || queue.length === 0 || current >= queue.length) return
    const item = queue[current]
    if (!item?.card) return
    const qSide = questionSideFromDirection(item.direction)
    const text = qSide === 'a' ? item.card.sideA : item.card.sideB
    dispatchPracticeFaceShown(item.card, qSide, text)
  }, [current, mode, queue]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!revealed || queue.length === 0 || current >= queue.length) return
    const item = queue[current]
    if (!item?.card) return
    const aSide = answerSideFromDirection(item.direction)
    const text = aSide === 'a' ? item.card.sideA : item.card.sideB
    dispatchPracticeFaceShown(item.card, aSide, text)
  }, [revealed, current, queue]) // eslint-disable-line react-hooks/exhaustive-deps

  async function handleRate(rating) {
    playRatingSound(rating)
    return runPracticeMutation(async () => {
      const item = queue[current]
      if (item?.replayOnly) {
        await consumeReplayOnlyItem(item)
        return
      }

      const responseTimeMs = (!item?.isNew && revealTimeRef.current !== null)
        ? revealTimeRef.current
        : null

      if (isTimedOut(responseTimeMs, responseTimeThresholdsRef.current.timeoutMs)) {
        timingLogRef.current = [
          ...timingLogRef.current,
          {
            cardId: item.cardId,
            sideA: item.card?.sideA ?? '',
            originalRating: rating,
            appliedRating: null,
            responseTimeSec: responseTimeMs !== null ? Math.round(responseTimeMs / 1000) : null,
            timedOut: true,
          },
        ]
        const timeoutState = {
          queue,
          current,
          stats: statsRef.current,
          cardProgress: cardProgressRef.current,
          retryQueueItems: retryQueueItemsRef.current,
          masteredQueue: masteredQueueRef.current,
          distribution: distributionRef.current,
          firstRatedIds: Array.from(firstRatedToday.current),
          pendingReplay: pendingReplayRef.current,
        }
        const nextT = applyTimeoutRequeue(timeoutState, item)
        setRetryQueueItems(nextT.retryQueueItems)
        updateLatestHistoryLock(!nextT.nextFinished ? nextT.queue[nextT.nextIndex] : null)
        setQueue(nextT.queue)
        if (nextT.nextFinished) setPracticeFinished(true)
        else {
          setPracticeFinished(false)
          advanceTo(nextT.nextIndex)
        }
        await saveCurrentPracticeSession({
          queue: nextT.queue,
          current: nextT.current,
          revealed: false,
          practiceFinished: nextT.nextFinished,
          stats: statsRef.current,
          firstRatedIds: Array.from(firstRatedToday.current),
          cardProgressState: cardProgressRef.current,
          retryQueueItems: nextT.retryQueueItems,
          masteredQueue: masteredQueueRef.current,
          history: historyRef.current,
          pendingReplay: pendingReplayRef.current,
          distributionState: distributionRef.current,
        })
        return
      }

      const appliedRating = computeAppliedRating(rating, responseTimeMs, responseTimeThresholdsRef.current)

      timingLogRef.current = [
        ...timingLogRef.current,
        {
          cardId: item.cardId,
          sideA: item.card?.sideA ?? '',
          originalRating: rating,
          appliedRating,
          responseTimeSec: responseTimeMs !== null ? Math.round(responseTimeMs / 1000) : null,
          timedOut: false,
        },
      ]

      const activeReplay = pendingReplayRef.current?.sourceItemKey === item.itemKey
        ? clonePendingReplay(pendingReplayRef.current)
        : null
      if (pendingReplayRef.current && !activeReplay) {
        clearPendingReplay()
      }

      const cardId = item.cardId
      const identity = practiceIdentity(item)
      const wasFirstRated = !firstRatedToday.current.has(identity)
      const firstRatedIdsBefore = Array.from(firstRatedToday.current)
      const prevStats = { ...statsRef.current }
      const snapshotCard = hasCompleteDirectionProgresses(item.card)
        ? item.card
        : await getCard(cardId)
      const prevSnapshot = {
        itemKey: item.itemKey,
        direction: item.direction,
        directionProgresses: cloneDirectionProgresses(snapshotCard?.directionProgresses),
      }
      pushHistoryEntry({
        queue: [...queue],
        current,
        stats: prevStats,
        cardProgressState: cloneCardProgressState(cardProgressRef.current),
        masteredQueue: [...masteredQueue],
        retryQueueItems: [...retryQueueItemsRef.current],
        cardId,
        direction: item.direction,
        prevProgress: prevSnapshot,
        addedToFirstRated: wasFirstRated,
        cardModifiedInDb: wasFirstRated,
        revealed,
      })

      let backendResult = {}
      if (!firstRatedToday.current.has(identity)) {
        const progressResult = await rateCardFsrs(cardId, item.itemKey, item.direction, appliedRating)
        firstRatedToday.current.add(identity)
        const refreshedCard = progressResult?.card ?? item.card
        backendResult = {
          refreshedCard,
          graduated: progressResult?.graduated === true,
        }
      }

      const currentState = {
        queue,
        current,
        stats: statsRef.current,
        cardProgress: cardProgressRef.current,
        retryQueueItems: retryQueueItemsRef.current,
        masteredQueue: masteredQueueRef.current,
        distribution: distributionRef.current,
        firstRatedIds: firstRatedIdsBefore,
        pendingReplay: pendingReplayRef.current,
      }
      const next = computeApplyRating(currentState, item, appliedRating, backendResult, rating)

      statsRef.current = next.stats
      replaceCardProgress(next.cardProgress)
      setRetryQueueItems(next.retryQueueItems)
      firstRatedToday.current = new Set(next.firstRatedIds)
      replaceDistributionState(next.distribution)
      masteredQueueRef.current = next.masteredQueue
      setMasteredQueue(masteredQueueRef.current)
      setPendingReplay(next.pendingReplay)

      updateLatestHistoryLock(!next.nextFinished ? next.queue[next.nextIndex] : null)
      setQueue(next.queue)
      if (next.nextFinished) setPracticeFinished(true)
      else {
        setPracticeFinished(false)
        advanceTo(next.nextIndex)
      }

      await saveCurrentPracticeSession({
        queue: next.queue,
        current: next.current,
        revealed: false,
        practiceFinished: next.nextFinished,
        stats: next.stats,
        firstRatedIds: next.firstRatedIds,
        cardProgressState: next.cardProgress,
        retryQueueItems: next.retryQueueItems,
        masteredQueue: masteredQueueRef.current,
        history: historyRef.current,
        pendingReplay: next.pendingReplay,
        distributionState: next.distribution,
      })
    })
  }

  async function handleFamiliar() {
    playRetrySound(true)
    return runPracticeMutation(async () => {
      const item = queue[current]
      clearPendingReplay()
      pushHistoryEntry({
        queue: [...queue],
        current,
        stats: { ...statsRef.current },
        cardProgressState: cloneCardProgressState(cardProgressRef.current),
        masteredQueue: [...masteredQueue],
        retryQueueItems: [...retryQueueItemsRef.current],
        cardId: item.cardId,
        prevProgress: {
          itemKey: item.itemKey,
          direction: item.direction,
          directionProgresses: cloneDirectionProgresses(item.card.directionProgresses),
        },
        addedToFirstRated: false,
        cardModifiedInDb: true,
        revealed,
      })
      await moveToMastered(item.cardId)

      const currentState = {
        queue,
        current,
        stats: statsRef.current,
        cardProgress: cardProgressRef.current,
        retryQueueItems: retryQueueItemsRef.current,
        masteredQueue: masteredQueueRef.current,
        distribution: distributionRef.current,
        firstRatedIds: Array.from(firstRatedToday.current),
        pendingReplay: null,
      }
      const next = computeApplyFamiliar(currentState, item)

      statsRef.current = next.stats
      replaceCardProgress(next.cardProgress)
      replaceDistributionState(next.distribution)
      setRetryQueueItems(next.retryQueueItems)
      masteredQueueRef.current = next.masteredQueue
      setMasteredQueue(masteredQueueRef.current)
      setPendingReplay(next.pendingReplay)

      setFamiliarToast(true)
      setTimeout(() => setFamiliarToast(false), 1800)

      updateLatestHistoryLock(!next.nextFinished ? next.queue[next.nextIndex] : null)
      setQueue(next.queue)
      if (next.nextFinished) setPracticeFinished(true)
      else advanceTo(next.nextIndex)

      await saveCurrentPracticeSession({
        queue: next.queue,
        current: next.current,
        revealed: false,
        practiceFinished: next.nextFinished,
        masteredQueue: masteredQueueRef.current,
        stats: next.stats,
        cardProgressState: next.cardProgress,
        retryQueueItems: next.retryQueueItems,
        history: historyRef.current,
        pendingReplay: null,
        distributionState: next.distribution,
      })
    })
  }

  async function handleRetry(passed) {
    playRetrySound(passed)
    return runPracticeMutation(async () => {
      const item = queue[current]
      if (item?.replayOnly) {
        await consumeReplayOnlyItem(item)
        return
      }
      clearPendingReplay()
      pushHistoryEntry({
        queue: [...queue],
        current,
        stats: { ...statsRef.current },
        cardProgressState: cloneCardProgressState(cardProgressRef.current),
        masteredQueue: [...masteredQueue],
        retryQueueItems: [...retryQueueItemsRef.current],
        cardId: item.cardId,
        prevProgress: null,
        addedToFirstRated: false,
        cardModifiedInDb: false,
        revealed,
      })
      const currentState = {
        queue,
        current,
        stats: statsRef.current,
        cardProgress: cardProgressRef.current,
        retryQueueItems: retryQueueItemsRef.current,
        masteredQueue: masteredQueueRef.current,
        distribution: distributionRef.current,
        firstRatedIds: Array.from(firstRatedToday.current),
        pendingReplay: null,
      }
      const next = computeApplyRetry(currentState, item, passed)

      replaceDistributionState(next.distribution)
      setRetryQueueItems(next.retryQueueItems)
      setPendingReplay(next.pendingReplay)
      updateLatestHistoryLock(!next.nextFinished ? next.queue[next.nextIndex] : null)
      setQueue(next.queue)
      if (next.nextFinished) setPracticeFinished(true)
      else {
        setPracticeFinished(false)
        advanceTo(next.nextIndex)
      }

      await saveCurrentPracticeSession({
        queue: next.queue,
        current: next.current,
        revealed: false,
        practiceFinished: next.nextFinished,
        stats: next.stats,
        firstRatedIds: next.firstRatedIds,
        cardProgressState: next.cardProgress,
        retryQueueItems: next.retryQueueItems,
        history: historyRef.current,
        pendingReplay: null,
        distributionState: next.distribution,
      })
    })
  }

  async function handleRetrySlowRecall() {
    playGenericClick()
    return runPracticeMutation(async () => {
      const item = queue[current]
      if (item?.replayOnly) {
        await consumeReplayOnlyItem(item)
        return
      }
      clearPendingReplay()
      pushHistoryEntry({
        queue: [...queue],
        current,
        stats: { ...statsRef.current },
        cardProgressState: cloneCardProgressState(cardProgressRef.current),
        masteredQueue: [...masteredQueue],
        retryQueueItems: [...retryQueueItemsRef.current],
        cardId: item.cardId,
        prevProgress: null,
        addedToFirstRated: false,
        cardModifiedInDb: false,
        revealed,
      })
      const currentState = {
        queue,
        current,
        stats: statsRef.current,
        cardProgress: cardProgressRef.current,
        retryQueueItems: retryQueueItemsRef.current,
        masteredQueue: masteredQueueRef.current,
        distribution: distributionRef.current,
        firstRatedIds: Array.from(firstRatedToday.current),
        pendingReplay: null,
      }
      const next = computeApplyRetrySlowRecall(currentState, item)

      setRetryQueueItems(next.retryQueueItems)
      setPendingReplay(next.pendingReplay)
      updateLatestHistoryLock(!next.nextFinished ? next.queue[next.nextIndex] : null)
      setQueue(next.queue)
      if (next.nextFinished) setPracticeFinished(true)
      else {
        setPracticeFinished(false)
        advanceTo(next.nextIndex)
      }

      await saveCurrentPracticeSession({
        queue: next.queue,
        current: next.current,
        revealed: false,
        practiceFinished: next.nextFinished,
        stats: next.stats,
        firstRatedIds: next.firstRatedIds,
        cardProgressState: next.cardProgress,
        retryQueueItems: next.retryQueueItems,
        history: historyRef.current,
        pendingReplay: null,
        distributionState: next.distribution,
      })
    })
  }

  async function handleGoBack() {
    playGenericClick()
    return runPracticeMutation(async () => {
      const last = historyRef.current.pop()
      if (!last) return
      const nextPendingReplay = last.lockedNextPresentation
        ? createPendingReplay(last.prevProgress?.itemKey ?? last.queue?.[last.current]?.itemKey, last.lockedNextPresentation)
        : null
      if (last.cardModifiedInDb && last.prevProgress) {
        await restoreCardFsrs(last.cardId, last.prevProgress)
      }
      if (last.addedToFirstRated) {
        firstRatedToday.current.delete(`${last.cardId}:${last.direction}`)
      }
      if (last.retryQueueItems) setRetryQueueItems(last.retryQueueItems)
      statsRef.current = { ...last.stats }
      replaceCardProgress(last.cardProgressState ?? createEmptyCardProgressState())
      replaceDistributionState(last.distributionState ?? buildDistributionState(last.queue))
      const restoredQueue = [...last.queue]
      queueRef.current = restoredQueue
      currentRef.current = last.current
      revealedRef.current = last.revealed ?? false
      masteredQueueRef.current = last.masteredQueue
      practiceFinishedRef.current = false
      setQueue(restoredQueue)
      setCurrent(last.current)
      setMasteredQueue(last.masteredQueue)
      setPracticeFinished(false)
      setRevealed(last.revealed ?? false)
      setCanGoBack(historyRef.current.length > 0)
      setPendingReplay(nextPendingReplay)
      if (scrollRef.current) scrollRef.current.scrollTop = 0
      await saveCurrentPracticeSession({
        queue: restoredQueue,
        current: last.current,
        revealed: last.revealed ?? false,
        practiceFinished: false,
        masteredQueue: last.masteredQueue,
        stats: last.stats,
        firstRatedIds: Array.from(firstRatedToday.current),
        cardProgressState: last.cardProgressState ?? createEmptyCardProgressState(),
        retryQueueItems: last.retryQueueItems ?? retryQueueItemsRef.current,
        history: historyRef.current,
        pendingReplay: nextPendingReplay,
      })
    })
  }

  useEffect(() => {
    let cancelled = false

    async function handlePracticeFinished() {
      if (!practiceFinished) return
      if (cancelled) return

      await finishPracticeToSummary()
    }

    handlePracticeFinished()

    return () => {
      cancelled = true
    }
  }, [practiceFinished, id, navigate]) // eslint-disable-line react-hooks/exhaustive-deps

  return {
    handleRate, handleRetry, handleRetrySlowRecall, handleFamiliar, handleGoBack, handleExit,
  }
}
