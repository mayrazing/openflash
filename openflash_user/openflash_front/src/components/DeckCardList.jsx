import { useTranslation } from 'react-i18next'
import CardItem from './CardItem'
import { isDeferredFilter } from '../lib/deckCardUtils.js'

/**
 * 卡片列表：含 today/tomorrow loading、空态、卡片 map、哨兵 sentinel + 加载更多。
 * pointerHandlers 统一传入，卡片 div 上绑 onPointerDown(e, card.id)，其余直接透传。
 */
export default function DeckCardList({
  displayedCards,
  filter,
  keyword,
  adding,
  todayLoading,
  tomorrowLoading,
  highlightedCardId,
  isSelectMode,
  selectedIds,
  deckId,
  loadingMore,
  sentinelRef,
  topSentinelRef,
  hasPrev,
  loadingPrev,
  pointerHandlers,
  onEdit,
  onDelete,
  onReset,
  onToggleSelect,
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-2 mb-4">
      {/* 顶部哨兵：处于末尾窗口（hasPrev）时滑到顶就逆向加载前一页 */}
      {hasPrev && (
        <>
          <div ref={topSentinelRef} />
          {loadingPrev && (
            <p className="py-3 text-center text-xs text-app-label-tertiary">{t('deckDetail.loadingMore')}</p>
          )}
        </>
      )}
      {filter === 'today' && todayLoading && (
        <p className="py-6 text-center text-app-label-tertiary">{t('deckDetail.loadingToday')}</p>
      )}
      {filter === 'tomorrow' && tomorrowLoading && (
        <p className="py-6 text-center text-app-label-tertiary">{t('deckDetail.loadingTomorrow')}</p>
      )}
      {displayedCards.length === 0 && !adding && !todayLoading && !tomorrowLoading && (
        <p className="py-8 text-center text-app-label-tertiary">
          {keyword || filter ? t('mastered.noMatchSearch') : t('deckDetail.emptyDeck')}
        </p>
      )}
      {displayedCards.map((card) => (
        <div
          key={card.id}
          data-card-id={card.id}
          onPointerDown={(e) => pointerHandlers.onPointerDown(e, card.id)}
          onPointerMove={pointerHandlers.onPointerMove}
          onPointerUp={pointerHandlers.onPointerUp}
          onPointerLeave={pointerHandlers.onPointerLeave}
          onPointerCancel={pointerHandlers.onPointerCancel}
          className={`long-press-select-surface rounded-2xl transition ${
            String(highlightedCardId) === String(card.id)
              ? 'ring-2 ring-app-focus ring-offset-2 ring-offset-app-background'
              : ''
          }`}
        >
          <CardItem
            card={card}
            onEdit={() => onEdit(card)}
            onDelete={() => onDelete(card.id)}
            onReset={() => onReset(card.id)}
            isSelectMode={isSelectMode}
            selected={selectedIds.includes(String(card.id))}
            onToggleSelect={() => onToggleSelect(card.id)}
            deckId={deckId}
          />
        </div>
      ))}
      {!isDeferredFilter(filter) && (
        <>
          <div ref={sentinelRef} />
          {loadingMore && (
            <p className="py-3 text-center text-xs text-app-label-tertiary">{t('deckDetail.loadingMore')}</p>
          )}
        </>
      )}
    </div>
  )
}
