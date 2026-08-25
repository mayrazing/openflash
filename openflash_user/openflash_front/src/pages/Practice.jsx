import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { usePracticeSession } from '../hooks/usePracticeSession'
import PracticeSessionLoading from '../components/practice/PracticeSessionLoading'
import PracticeModeSelect from '../components/practice/PracticeModeSelect'
import PracticeEmptyState from '../components/practice/PracticeEmptyState'
import PracticeActiveView from '../components/practice/PracticeActiveView'
import { countUniqueCards, questionSideFromDirection, answerSideFromDirection } from '../lib/practiceQueue'
import { countTotalCards, countCompletedCards, ratingsForPracticeItem } from '../lib/practiceSession.js'
import { playGenericClick, stopBgMusic } from '../lib/soundEngine'

// 32rem，与内容区 max-w-lg 对齐；侧栏宽度据此两侧均分
const LANDSCAPE_CONTENT_WIDTH_PX = 512
const MIN_SIDE_DISTRIBUTION_WIDTH_PX = 96
const BOTTOM_DISTRIBUTION_MIN_HEIGHT_PX = 48

function readViewport() {
  return {
    width: window.innerWidth,
    height: window.innerHeight,
  }
}

function useViewportSize() {
  const [viewport, setViewport] = useState(readViewport)

  useEffect(() => {
    const updateViewport = () => {
      const next = readViewport()
      setViewport(prev => (
        prev.width === next.width && prev.height === next.height ? prev : next
      ))
    }
    window.addEventListener('resize', updateViewport)
    window.visualViewport?.addEventListener('resize', updateViewport)
    return () => {
      window.removeEventListener('resize', updateViewport)
      window.visualViewport?.removeEventListener('resize', updateViewport)
    }
  }, [])

  return viewport
}

export default function Practice() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const s = usePracticeSession(id, navigate, t)
  const viewport = useViewportSize()

  if (!s.sessionReady || !s.practiceModesReady || s.startingPractice) {
    return (
      <PracticeSessionLoading
        t={t}
        onBack={() => {
          playGenericClick()
          stopBgMusic()
          navigate(`/deck/${id}`)
        }}
      />
    )
  }

  if (!s.mode) {
    return (
      <PracticeModeSelect
        availableModes={s.availableModes}
        resumePrompt={s.resumePrompt}
        todayRepracticeCards={s.todayRepracticeCards}
        onBack={() => { playGenericClick(); stopBgMusic(); navigate(`/deck/${id}`) }}
        onResume={s.handleResumeContinue}
        onStartFresh={s.handleStartFreshPractice}
        onStartTodayRepractice={s.handleStartTodayRepractice}
        t={t}
      />
    )
  }

  if (s.queue.length === 0) {
    return (
      <PracticeEmptyState
        onBack={() => { playGenericClick(); stopBgMusic(); navigate(`/deck/${id}`) }}
        t={t}
      />
    )
  }

  const item = s.queue[s.current]
  const isRetry = !!item?.isRepractice
  const retryDistributionCard = s.distributionState.cards.find(c => String(c.cardId) === String(item?.cardId))
  const retryCount = (retryDistributionCard?.directions?.[item?.direction]?.retryCompleted ?? []).filter(Boolean).length
  const retryTotal = Math.max(
    1,
    item?.retryCount
      ?? retryDistributionCard?.directions?.[item?.direction]?.retryCompleted?.length
      ?? 3
  )
  const availableRatings = ratingsForPracticeItem(s.ratings, s.distributionState, item)
  const totalCards = isRetry
    ? countUniqueCards(s.queue.filter(queueItem => queueItem?.isRepractice && !queueItem?.replayOnly))
    : countTotalCards(s.cardProgressRef.current)
  const completedCards = isRetry
    ? Math.max(totalCards - countUniqueCards(s.queue.slice(s.current).filter(queueItem => queueItem?.isRepractice && !queueItem?.replayOnly)), 0)
    : (s.practiceFinished ? totalCards : countCompletedCards(s.cardProgressRef.current))
  const remainingCards = Math.max(totalCards - completedCards, 0)
  const questionSide = questionSideFromDirection(item?.direction ?? 'a2b')
  const answerSide = answerSideFromDirection(item?.direction ?? 'a2b')
  const isPhoneLandscape = s.isLandscape && viewport.height < 500
  const distributionSideWidth = Math.max(0, (viewport.width - LANDSCAPE_CONTENT_WIDTH_PX) / 2 - 16)
  const showSideDistribution = s.isLandscape && distributionSideWidth >= MIN_SIDE_DISTRIBUTION_WIDTH_PX
  const bottomDistributionHeight = !showSideDistribution ? Math.max(s.distributionFrameHeight, BOTTOM_DISTRIBUTION_MIN_HEIGHT_PX) : 0
  const distributionBottomPadding = `calc(${bottomDistributionHeight}px + var(--app-bottom-bar-height) + var(--practice-distribution-gap))`

  return (
    <PracticeActiveView
      item={item}
      itemKey={item?.itemKey}
      isRetry={isRetry}
      retryCount={retryCount}
      retryTotal={retryTotal}
      totalCards={totalCards}
      completedCards={completedCards}
      remainingCards={remainingCards}
      questionSide={questionSide}
      answerSide={answerSide}
      revealed={s.revealed}
      isPhoneLandscape={isPhoneLandscape}
      distributionState={s.distributionState}
      distributionFrameRef={s.distributionFrameRef}
      distributionBottomPadding={distributionBottomPadding}
      showSideDistribution={showSideDistribution}
      distributionSideWidth={distributionSideWidth}
      scrollRef={s.scrollRef}
      ratings={availableRatings}
      canGoBack={s.canGoBack}
      familiarToast={s.familiarToast}
      deckId={id}
      onReveal={() => {
        playGenericClick()
        s.revealTimeRef.current = s.cardShownTimeRef.current !== null
          ? Date.now() - s.cardShownTimeRef.current
          : null
        s.cardShownTimeRef.current = null
        s.setRevealed(true)
      }}
      onFamiliar={!isRetry && !item?.replayOnly ? s.handleFamiliar : undefined}
      onRate={s.handleRate}
      onRetry={s.handleRetry}
      onRetrySlowRecall={s.handleRetrySlowRecall}
      onGoBack={s.handleGoBack}
      onExit={s.handleExit}
      distributionCollapsed={s.distributionCollapsed}
      onToggleDistribution={() => s.setDistributionCollapsed(v => !v)}
      t={t}
    />
  )
}
