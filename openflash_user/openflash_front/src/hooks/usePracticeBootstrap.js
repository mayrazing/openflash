import { useEffect } from 'react'
import {
  buildDailyQueue, getDeckSettings, getPracticeStartupSnapshot,
  getResponseTimeConfig, getPracticeModes, clearPracticeSession,
  savePracticeSession, getToday,
} from '../db/database'
import { playGenericClick, stopBgMusic } from '../lib/soundEngine'
import {
  buildItemKey, buildCardProgressState, buildDistributionState,
  createEmptyCardProgressState, createRetryItems,
  noConsecutiveShuffle,
} from '../lib/practiceSession.js'
import {
  buildSessionWithCurrentSettings,
  normalizeReviewLoadProfile,
} from '../lib/practiceSessionSettings.js'
import { shuffleAvoidingAdjacentCards } from '../lib/queueShuffle'
import { runPracticePrefetch } from '../plugins/practicePrefetch'
import {
  DEFAULT_MODES, TODAY_REPRACTICE_MODE, createEmptyStats,
  hasSavedSessionContinuation, normalizePracticeSessionMode,
  isTodayRepracticeSessionStillValid, isDisabledPracticeModeError,
  snapshotPracticeQueueItem,
} from '../lib/practiceQueue'

const PRACTICE_PREFETCH_START_BUDGET_MS = 180

/** 练习入口只短暂等待插件预热，超出预算则让预热留在后台继续跑，避免点击开始后长时间卡住。 */
function waitForPracticePrefetchStartBudget(deckId) {
  if (deckId == null) return Promise.resolve()
  return Promise.race([
    runPracticePrefetch(deckId),
    new Promise(resolve => setTimeout(resolve, PRACTICE_PREFETCH_START_BUDGET_MS)),
  ])
}

export function usePracticeBootstrap(ctx) {
  const {
    id, settings, setSettings,
    setTodayRepracticeCards, setSessionReady, setResumePrompt, setStartingPractice,
    setAvailableModes, setPracticeModesReady, resumePrompt, todayRepracticeCards,
    firstRatedToday, timingLogRef, sessionDateRef, settingsRef, responseTimeThresholdsRef,
    modeRef, queueRef, currentRef, revealedRef, practiceFinishedRef, masteredQueueRef,
    statsRef, setMode, setQueue, setCurrent, setRevealed, setPracticeFinished, setMasteredQueue,
    replaceCardProgress, replaceDistributionState, resetSessionContinuationState,
    resetHistory, clearPendingReplay, setRetryQueueItems,
    runPracticeMutation, saveCurrentPracticeSession, restoreSavedPracticeSession,
  } = ctx

  async function startPractice(selectedMode, overrideSettings, options = {}) {
    return runPracticeMutation(async () => {
      firstRatedToday.current = new Set()
      timingLogRef.current = []

      const s = overrideSettings ?? settings
      sessionDateRef.current = getToday()
      const queueData = await buildDailyQueue(id, s.newCardsPerDay, selectedMode)
      let combined = Array.isArray(queueData?.items) ? queueData.items.map(item => ({
        ...item,
        itemKey: item.itemKey ?? buildItemKey(item.cardId, item.direction, item.kind, item.ordinal ?? 0),
        kind: item.kind ?? 'base',
        ordinal: item.ordinal ?? 0,
        isRepractice: false,
      })) : []

      const normalizedQueue = shuffleAvoidingAdjacentCards(combined)
      resetSessionContinuationState()
      const initialCardProgressState = buildCardProgressState(
        combined.filter(item => !item.isRepractice),
        Array.from(firstRatedToday.current)
      )
      const initialDistributionState = buildDistributionState(normalizedQueue)
      replaceCardProgress(initialCardProgressState)
      replaceDistributionState(initialDistributionState)
      queueRef.current = normalizedQueue
      currentRef.current = 0
      revealedRef.current = false
      practiceFinishedRef.current = false
      masteredQueueRef.current = []
      setQueue(normalizedQueue)
      modeRef.current = selectedMode
      setMode(selectedMode)
      setCurrent(0)
      setRevealed(false)
      setPracticeFinished(false)
      setMasteredQueue([])
      statsRef.current = {
        ...createEmptyStats(),
        newCount: queueData?.newCardCount ?? 0,
        reviewCountStat: queueData?.reviewCardCount ?? 0,
      }
      await saveCurrentPracticeSession({
        mode: selectedMode,
        queue: normalizedQueue,
        current: 0,
        revealed: false,
        practiceFinished: false,
        masteredQueue: [],
        postRoundRetryActive: false,
        stats: statsRef.current,
        firstRatedIds: Array.from(firstRatedToday.current),
        cardProgressState: initialCardProgressState,
        settingsNewCardsPerDay: s.newCardsPerDay,
        settingsReviewLoadProfile: normalizeReviewLoadProfile(s.reviewLoadProfile),
        retryQueueItems: [],
        postRoundRetryCards: [],
        history: [],
        pendingReplay: null,
      })
    }, { nested: options.nested })
  }

  async function startTodayRepractice(options = {}) {
    return runPracticeMutation(async () => {
      if (todayRepracticeCards.length === 0) return

      firstRatedToday.current = new Set()
      sessionDateRef.current = getToday()
      resetSessionContinuationState()
      await clearPracticeSession(id)

      const baseItems = todayRepracticeCards.flatMap(card => ([
        {
          itemKey: buildItemKey(card.id, 'a2b', 'base', 0),
          cardId: card.id,
          direction: 'a2b',
          kind: 'base',
          ordinal: 0,
          card,
          isNew: false,
          isReview: true,
          isRepractice: false,
        },
        {
          itemKey: buildItemKey(card.id, 'b2a', 'base', 0),
          cardId: card.id,
          direction: 'b2a',
          kind: 'base',
          ordinal: 0,
          card,
          isNew: false,
          isReview: true,
          isRepractice: false,
        },
      ]))
      const retryItems = baseItems.flatMap(item => createRetryItems(item, 'todayRepractice'))
      const nextQueue = noConsecutiveShuffle(retryItems)
      const emptyCardProgressState = createEmptyCardProgressState()
      const initialDistributionState = buildDistributionState(nextQueue)

      replaceCardProgress(emptyCardProgressState)
      replaceDistributionState(initialDistributionState)
      queueRef.current = nextQueue
      currentRef.current = 0
      revealedRef.current = false
      practiceFinishedRef.current = false
      masteredQueueRef.current = []
      modeRef.current = TODAY_REPRACTICE_MODE
      statsRef.current = {
        ...createEmptyStats(),
        reviewCountStat: todayRepracticeCards.length,
      }

      setQueue(nextQueue)
      setMode(TODAY_REPRACTICE_MODE)
      setCurrent(0)
      setRevealed(false)
      setPracticeFinished(false)
      setMasteredQueue([])

      await saveCurrentPracticeSession({
        mode: TODAY_REPRACTICE_MODE,
        queue: nextQueue,
        current: 0,
        revealed: false,
        practiceFinished: false,
        masteredQueue: [],
        postRoundRetryActive: false,
        stats: statsRef.current,
        firstRatedIds: [],
        cardProgressState: emptyCardProgressState,
        settingsNewCardsPerDay: settingsRef.current.newCardsPerDay,
        settingsReviewLoadProfile: normalizeReviewLoadProfile(settingsRef.current.reviewLoadProfile),
        retryQueueItems: nextQueue.map(snapshotPracticeQueueItem),
        postRoundRetryCards: [],
        history: [],
        pendingReplay: null,
      })
    }, { nested: options.nested })
  }

  async function handleResumeContinue() {
    playGenericClick()
    stopBgMusic()
    setStartingPractice(true)
    await waitForPracticePrefetchStartBudget(id)
    return runPracticeMutation(async () => {
      if (!resumePrompt) return
      const prompt = resumePrompt
      setResumePrompt(null)
      await restoreSavedPracticeSession(prompt.payload)
    }).finally(() => {
      setStartingPractice(false)
    })
  }

  async function handleStartFreshPractice(selectedMode) {
    playGenericClick()
    stopBgMusic()
    setStartingPractice(true)
    await waitForPracticePrefetchStartBudget(id)
    return runPracticeMutation(async () => {
      setResumePrompt(null)
      await clearPracticeSession(id)
      await startPractice(selectedMode, undefined, { nested: true })
    }).finally(() => {
      setStartingPractice(false)
    })
  }

  async function handleStartTodayRepractice() {
    playGenericClick()
    stopBgMusic()
    setStartingPractice(true)
    await waitForPracticePrefetchStartBudget(id)
    return startTodayRepractice().finally(() => {
      setStartingPractice(false)
    })
  }

  useEffect(() => {
    let cancelled = false

    async function initPractice() {
      const s = await getDeckSettings(id)
      if (cancelled) return
      setSettings(s)

      const [startupSnapshot, rtConfig] = await Promise.all([
        getPracticeStartupSnapshot(id, s.newCardsPerDay),
        getResponseTimeConfig().catch(() => null),
      ])
      if (rtConfig) {
        responseTimeThresholdsRef.current = {
          timeoutMs: (rtConfig.timeoutSeconds ?? 60) * 1000,
          grade3SlowMs: (rtConfig.grade3SlowThresholdSeconds ?? 5) * 1000,
          grade2SlowMs: (rtConfig.grade2SlowThresholdSeconds ?? 10) * 1000,
        }
      }
      if (cancelled) return
      const savedSession = normalizePracticeSessionMode(startupSnapshot.savedSession)
      const { pendingSummary, todayCards } = startupSnapshot
      setTodayRepracticeCards(
        pendingSummary?.pendingTotal === 0 && todayCards.length > 0 ? todayCards : []
      )

      if (savedSession?.mode === TODAY_REPRACTICE_MODE && !isTodayRepracticeSessionStillValid(savedSession, pendingSummary, todayCards)) {
        resetHistory()
        clearPendingReplay()
        setRetryQueueItems([])
        await clearPracticeSession(id)
        if (cancelled) return
        setSessionReady(true)
        return
      }

      if (hasSavedSessionContinuation(savedSession)) {
        let reconciledSession
        let changed = false
        try {
          const result = await buildSessionWithCurrentSettings(savedSession, s, id, {
            buildDailyQueue,
            getToday,
          })
          reconciledSession = result.session
          changed = result.changed
        } catch (error) {
          if (!isDisabledPracticeModeError(error)) {
            throw error
          }
          resetHistory()
          clearPendingReplay()
          setRetryQueueItems([])
          await clearPracticeSession(id)
          if (cancelled) return
          setSessionReady(true)
          return
        }
        if (!hasSavedSessionContinuation(reconciledSession)) {
          resetHistory()
          clearPendingReplay()
          setRetryQueueItems([])
          await clearPracticeSession(id)
          if (cancelled) return
          setSessionReady(true)
          return
        }
        if (changed) {
          await savePracticeSession(id, reconciledSession)
          if (cancelled) return
        }
        setResumePrompt({ type: 'session', payload: reconciledSession, settings: s })
        setSessionReady(true)
        return
      }

      clearPendingReplay()
      setSessionReady(true)
    }

    initPractice()

    return () => {
      cancelled = true
    }
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    let cancelled = false

    async function loadPracticeModes() {
      try {
        const modes = await getPracticeModes()
        const safeModes = Array.isArray(modes)
          ? modes.filter(mode => mode?.value && mode?.label)
          : []
        if (!cancelled && safeModes.length > 0) {
          setAvailableModes(safeModes)
        }
      } catch {
        if (!cancelled) {
          setAvailableModes(DEFAULT_MODES)
        }
      } finally {
        if (!cancelled) {
          setPracticeModesReady(true)
        }
      }
    }

    loadPracticeModes()

    return () => {
      cancelled = true
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  return {
    startPractice, startTodayRepractice, handleResumeContinue,
    handleStartFreshPractice, handleStartTodayRepractice,
  }
}
