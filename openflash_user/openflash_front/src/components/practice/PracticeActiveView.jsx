import { Button, Glass, Progressbar } from 'konsta/react'
import PracticeCard from '../PracticeCard'
import PracticeDistributionGrid from './PracticeDistributionGrid'
import BottomActionBar from '../layout/BottomActionBar'

export default function PracticeActiveView({
  item,
  itemKey,
  isRetry,
  retryCount,
  retryTotal,
  totalCards,
  completedCards,
  remainingCards,
  questionSide,
  answerSide,
  revealed,
  isPhoneLandscape,
  distributionState,
  distributionFrameRef,
  distributionBottomPadding,
  showSideDistribution,
  distributionSideWidth,
  scrollRef,
  ratings,
  canGoBack,
  familiarToast,
  deckId,
  onReveal,
  onFamiliar,
  onRate,
  onRetry,
  onRetrySlowRecall,
  onGoBack,
  onExit,
  distributionCollapsed,
  onToggleDistribution,
  t,
}) {
  const showBottomDistribution = !showSideDistribution
  return (
    <div className="max-w-lg mx-auto flex h-full flex-col pt-4">
      {/* 顶部进度条 */}
      <div className="shrink-0 bg-app-background px-4 pt-5 pb-3">
        <div className="flex items-center gap-3">
          <Glass className="shrink-0 transform transform-gpu rounded-full">
            <Button inline small clear rounded onClick={onExit}>
              {t('practice.exit')}
            </Button>
          </Glass>
          <div className="flex-1">
            <div className="mb-1 flex justify-between text-sm text-app-label-secondary">
              <span>
                {isRetry
                  ? <span className="text-app-warning">{t('practice.retry')}</span>
                  : item.isNew ? t('summary.newCards') : t('summary.reviewCards')}
              </span>
              <span>
                {t('practice.cardsRemaining', { remaining: remainingCards, total: totalCards })}
              </span>
            </div>
            <Progressbar
              className="!h-2"
              progress={completedCards / Math.max(totalCards, 1)}
              colors={{
                trackBgIos: 'bg-app-surface-tertiary',
                activeBgIos: 'bg-app-accent-fill',
              }}
            />
          </div>
          {canGoBack && (
            <Glass className="shrink-0 transform transform-gpu rounded-full">
              <Button inline small clear rounded onClick={onGoBack}>
                {t('practice.prevCard')}
              </Button>
            </Glass>
          )}
        </div>
      </div>

      {/* 可滚动内容区，底部留白跟随分布图高度，避免被固定底栏和分布图遮挡 */}
      <div
        ref={scrollRef}
        className="min-h-0 flex-1 flex flex-col overflow-y-auto overscroll-none bg-app-surface-primary"
      >
        {item?.card && (
          <PracticeCard
            key={item.itemKey}
            card={item.card}
            itemKey={itemKey}
            questionSide={questionSide}
            answerSide={answerSide}
            isRetry={isRetry}
            retryCount={retryCount}
            retryTotal={retryTotal}
            revealed={revealed}
            deckId={deckId}
            onReveal={onReveal}
            onFamiliar={onFamiliar}
          />
        )}
        <div style={{ height: distributionBottomPadding }} className="shrink-0" />
      </div>

      {showSideDistribution && (
        <div
          className="fixed top-1/2 -translate-y-1/2 overflow-y-auto"
          style={{ left: '0.5rem', width: distributionSideWidth, maxHeight: '100dvh' }}
        >
          <PracticeDistributionGrid
            distribution={distributionState}
            frameRef={distributionFrameRef}
            activeCardId={item?.cardId}
          />
        </div>
      )}
      {showBottomDistribution && (
        <div
          className="fixed left-0 right-0 px-4"
          style={{ bottom: 'var(--practice-bottom-distribution-offset)' }}
        >
          <div className="max-w-lg mx-auto relative">
            {distributionState?.cards?.length > 0 && (
              <button
                type="button"
                onClick={onToggleDistribution}
                aria-label={distributionCollapsed ? t('practice.distribution.expand') : t('practice.distribution.collapse')}
                aria-expanded={!distributionCollapsed}
                className="absolute left-1/2 -translate-x-1/2 -top-3 z-10 flex items-center h-6 px-3 rounded-full border border-app-separator bg-app-surface-secondary shadow-sm active:bg-app-surface-tertiary transition-colors"
              >
                <svg className={`w-3.5 h-3.5 text-app-label-tertiary transition-transform duration-300 ${distributionCollapsed ? 'rotate-180' : ''}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="6 15 12 9 18 15"/></svg>
              </button>
            )}
            <PracticeDistributionGrid
              distribution={distributionState}
              frameRef={distributionFrameRef}
              activeCardId={item?.cardId}
              collapsed={distributionCollapsed}
            />
          </div>
        </div>
      )}

      {/* 底部按钮区，固定在视口底部 */}
      <BottomActionBar tone="surface" dialogBoundary>
        {revealed ? (
          isRetry ? (
            <div className="grid grid-cols-3 gap-2">
              <button onClick={() => onRetry(false)}
                className={`${isPhoneLandscape ? 'py-1.5' : 'py-3'} rounded-2xl bg-app-practice-again-tonal text-sm font-medium text-app-practice-again active:bg-app-practice-again-pressed transition-colors`}>
                {t('practice.retryNo')}
              </button>
              <button onClick={onRetrySlowRecall}
                className={`${isPhoneLandscape ? 'py-1.5' : 'py-3'} rounded-2xl bg-app-practice-hard-tonal text-sm font-medium text-app-practice-hard active:bg-app-practice-hard-pressed transition-colors`}>
                {t('practice.retrySlow')}
              </button>
              <button onClick={() => onRetry(true)}
                className={`${isPhoneLandscape ? 'py-1.5' : 'py-3'} rounded-2xl bg-app-practice-easy-tonal text-sm font-medium text-app-practice-easy active:bg-app-practice-easy-pressed transition-colors`}>
                {t('practice.retryYes')}
              </button>
            </div>
          ) : (
            <div
              className="grid gap-2"
              style={{ gridTemplateColumns: `repeat(${ratings.length}, minmax(0, 1fr))` }}
            >
              {ratings.map((r) => (
                <button key={r.value} onClick={() => onRate(r.value)}
                  className={`${isPhoneLandscape ? 'py-1.5' : 'py-3'} rounded-2xl text-sm font-medium ${r.color} transition-colors flex flex-col items-center gap-0.5`}>
                  <span>{r.label}</span>
                  <span className="text-xs font-normal leading-tight text-app-label-secondary">{r.hint}</span>
                </button>
              ))}
            </div>
          )
        ) : (
          <div className="h-12" />
        )}
      </BottomActionBar>

      {/* 熟悉提示 toast */}
      {familiarToast && (
        <div className="fixed top-6 left-1/2 -translate-x-1/2 z-50 px-5 py-2.5 bg-app-success-fill text-app-on-success text-sm rounded-2xl shadow-lg pointer-events-none">
          {t('practice.masteredToast')}
        </div>
      )}

    </div>
  )
}
