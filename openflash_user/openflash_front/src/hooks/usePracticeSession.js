import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import useModalBodyLock from '../lib/useModalBodyLock'
import { getToday } from '../db/database'
import { DEFAULT_THRESHOLDS } from '../lib/practiceResponseTime'
import { clonePendingReplay } from '../lib/practiceReplay'
import {
  createEmptyCardProgressState, createEmptyDistributionState,
  cloneCardProgressState, cloneDistributionState,
} from '../lib/practiceSession.js'
import { createRatings, createEmptyStats, clampHistoryEntries, DEFAULT_MODES } from '../lib/practiceQueue'
import { startBgMusic, stopBgMusic } from '../lib/soundEngine'
import { usePracticePersistence } from './usePracticePersistence'
import { usePracticeBootstrap } from './usePracticeBootstrap'
import { usePracticeEngine } from './usePracticeEngine'
import { usePracticeDayRollover } from './usePracticeDayRollover'

export function usePracticeSession(id, navigate, t) {
  const [mode, setMode] = useState(null)
  const modeRef = useRef(null)
  const [queue, setQueue] = useState([])
  const [current, setCurrent] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [practiceFinished, setPracticeFinished] = useState(false)
  const firstRatedToday = useRef(new Set())
  const [settings, setSettings] = useState({ newCardsPerDay: 10, targetRetention: 0.9 })
  const [masteredQueue, setMasteredQueue] = useState([])
  const statsRef = useRef(createEmptyStats())
  const scrollRef = useRef(null)
  const [familiarToast, setFamiliarToast] = useState(false)
  const historyRef = useRef([])
  const retryQueueItemsRef = useRef([])
  const revealTimeRef = useRef(null)
  const cardShownTimeRef = useRef(null)
  const timingLogRef = useRef([])
  const responseTimeThresholdsRef = useRef({ ...DEFAULT_THRESHOLDS })
  const [canGoBack, setCanGoBack] = useState(false)
  const [resumePrompt, setResumePrompt] = useState(null)
  useModalBodyLock(!!resumePrompt)
  const [sessionReady, setSessionReady] = useState(false)
  const [startingPractice, setStartingPractice] = useState(false)
  const [availableModes, setAvailableModes] = useState(DEFAULT_MODES)
  const [practiceModesReady, setPracticeModesReady] = useState(false)
  const [todayRepracticeCards, setTodayRepracticeCards] = useState([])
  const cardProgressRef = useRef(createEmptyCardProgressState())
  const [, setCardProgressVersion] = useState(0)
  const [distributionState, setDistributionState] = useState(createEmptyDistributionState())
  const distributionRef = useRef(distributionState)
  const distributionFrameRef = useRef(null)
  const [distributionFrameHeight, setDistributionFrameHeight] = useState(0)
  const [isLandscape, setIsLandscape] = useState(() => window.matchMedia('(orientation: landscape)').matches)
  // 会话级：用户本次练习内是否收起底部分布网格；不持久化，退出练习即重置为展开
  const [distributionCollapsed, setDistributionCollapsed] = useState(false)
  const ratings = createRatings(t)
  const settingsRef = useRef(settings)
  const queueRef = useRef(queue)
  const currentRef = useRef(current)
  const revealedRef = useRef(revealed)
  const practiceFinishedRef = useRef(practiceFinished)
  const masteredQueueRef = useRef(masteredQueue)
  const sessionMutationChainRef = useRef(Promise.resolve())
  const sessionSaveChainRef = useRef(Promise.resolve())
  const sessionSaveVersionRef = useRef(0)
  const pendingReplayRef = useRef(null)
  const sessionDateRef = useRef(getToday())
  const practiceDayRolloverRef = useRef(false)

  useEffect(() => {
    settingsRef.current = settings
  }, [settings])

  useEffect(() => {
    if (sessionReady && !mode && !startingPractice) {
      startBgMusic()
      return () => stopBgMusic()
    }
    stopBgMusic()
    return undefined
  }, [sessionReady, mode, startingPractice])

  useEffect(() => {
    const mq = window.matchMedia('(orientation: landscape)')
    const handler = (e) => setIsLandscape(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  function resetHistory(entries = []) {
    historyRef.current = clampHistoryEntries(entries)
    setCanGoBack(historyRef.current.length > 0)
  }

  function pushHistoryEntry(entry) {
    historyRef.current = clampHistoryEntries([
      ...historyRef.current,
      {
        ...entry,
        distributionState: entry.distributionState ?? cloneDistributionState(distributionRef.current),
      },
    ])
    setCanGoBack(historyRef.current.length > 0)
  }

  function replaceDistributionState(nextDistributionState) {
    const clonedState = cloneDistributionState(nextDistributionState)
    distributionRef.current = clonedState
    setDistributionState(clonedState)
  }

  function setRetryQueueItems(items = []) {
    retryQueueItemsRef.current = items
  }

  function setPendingReplay(next = null) {
    pendingReplayRef.current = clonePendingReplay(next)
  }

  function clearPendingReplay() {
    pendingReplayRef.current = null
  }

  function invalidateQueuedPracticeSessionSaves() {
    sessionSaveVersionRef.current += 1
  }

  function resetSessionContinuationState() {
    invalidateQueuedPracticeSessionSaves()
    resetHistory()
    clearPendingReplay()
    setRetryQueueItems([])
    replaceDistributionState(createEmptyDistributionState())
  }

  useEffect(() => {
    distributionRef.current = distributionState
  }, [distributionState])

  useLayoutEffect(() => {
    const frame = distributionFrameRef.current
    if (!frame) {
      setDistributionFrameHeight(0)
      return undefined
    }

    const updateFrameHeight = () => {
      setDistributionFrameHeight(Math.ceil(frame.getBoundingClientRect().height))
    }
    updateFrameHeight()

    if (typeof ResizeObserver === 'undefined') return undefined
    const observer = new ResizeObserver(updateFrameHeight)
    observer.observe(frame)
    return () => observer.disconnect()
  }, [distributionState.cards.length])

  useEffect(() => {
    queueRef.current = queue
    currentRef.current = current
    revealedRef.current = revealed
    practiceFinishedRef.current = practiceFinished
    masteredQueueRef.current = masteredQueue
  }, [queue, current, revealed, practiceFinished, masteredQueue])

  function replaceCardProgress(nextProgressState) {
    cardProgressRef.current = cloneCardProgressState(nextProgressState)
    setCardProgressVersion(version => version + 1)
  }

  function advanceTo(nextIndex) {
    currentRef.current = nextIndex
    revealedRef.current = false
    setCurrent(nextIndex)
    setRevealed(false)
    if (scrollRef.current) scrollRef.current.scrollTop = 0
  }

  function dispatchPracticeFaceShown(card, side, text) {
    window.dispatchEvent(new CustomEvent('practice:face-shown', {
      detail: { deckId: id, cardId: card.id, side, text },
    }))
  }

  const ctx = {
    // 入参
    id, navigate, t,
    // all state
    mode, setMode,
    modeRef,
    queue, setQueue,
    current, setCurrent,
    revealed, setRevealed,
    practiceFinished, setPracticeFinished,
    firstRatedToday,
    settings, setSettings,
    masteredQueue, setMasteredQueue,
    statsRef,
    scrollRef,
    familiarToast, setFamiliarToast,
    historyRef,
    retryQueueItemsRef,
    revealTimeRef,
    cardShownTimeRef,
    timingLogRef,
    responseTimeThresholdsRef,
    canGoBack, setCanGoBack,
    resumePrompt, setResumePrompt,
    sessionReady, setSessionReady,
    startingPractice, setStartingPractice,
    availableModes, setAvailableModes,
    practiceModesReady, setPracticeModesReady,
    todayRepracticeCards, setTodayRepracticeCards,
    cardProgressRef,
    setCardProgressVersion,
    distributionState, setDistributionState,
    distributionRef,
    distributionFrameRef,
    distributionFrameHeight, setDistributionFrameHeight,
    isLandscape, setIsLandscape,
    distributionCollapsed, setDistributionCollapsed,
    ratings,
    settingsRef,
    queueRef,
    currentRef,
    revealedRef,
    practiceFinishedRef,
    masteredQueueRef,
    sessionMutationChainRef,
    sessionSaveChainRef,
    sessionSaveVersionRef,
    pendingReplayRef,
    sessionDateRef,
    practiceDayRolloverRef,
    // shared functions
    resetHistory,
    pushHistoryEntry,
    replaceDistributionState,
    setRetryQueueItems,
    setPendingReplay,
    clearPendingReplay,
    invalidateQueuedPracticeSessionSaves,
    resetSessionContinuationState,
    replaceCardProgress,
    advanceTo,
    dispatchPracticeFaceShown,
  }

  const persistence = usePracticePersistence(ctx)
  Object.assign(ctx, persistence)

  usePracticeDayRollover(ctx)

  const bootstrap = usePracticeBootstrap(ctx)
  Object.assign(ctx, bootstrap)

  const engine = usePracticeEngine(ctx)
  Object.assign(ctx, engine)

  return ctx
}
