import { useTranslation } from 'react-i18next'
import { Button, Card } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'

export default function DeckCard({
  deck,
  masteredCount,
  totalCount,
  onClick,
  onRename,
  onDelete,
  isSelectMode = false,
  selected = false,
  onToggleSelect,
}) {
  const { t } = useTranslation()
  /**
   * 卡包在普通模式下点击进入详情；选择模式下点击只切换选中。
   */
  function handleClick(event) {
    if (isSelectMode) {
      onToggleSelect?.(event)
      return
    }
    onClick?.(event)
  }

  return (
    <Card
      data-pointer-activation=""
      raised
      outline
      contentWrapPadding="p-4"
      className={`!mx-0 !my-0 cursor-pointer transition-all ${
        isSelectMode
          ? selected
            ? 'border-app-selected-border bg-app-selected ring-2 ring-app-selected-border'
            : ''
          : 'active:scale-[0.99]'
      }`}
      onClick={withGenericClick(handleClick)}
    >
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-[17px] font-semibold">{deck.name}</p>
          <p className="mt-0.5 text-sm text-app-label-secondary">{t('deckCard.cardCount', { mastered: masteredCount, total: totalCount })}</p>
        </div>
        {isSelectMode ? (
          selected && (
            <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-app-accent-fill text-sm text-app-on-accent">✓</div>
          )
        ) : (
          <div className="flex shrink-0 items-center gap-1">
            <Button
              inline
              small
              clear
              rounded
              onClick={withGenericClick((event) => { event.stopPropagation(); onRename() })}
            >
              {t('deckCard.rename')}
            </Button>
            <Button
              inline
              small
              clear
              rounded
              className="text-app-danger"
              onClick={withGenericClick((event) => { event.stopPropagation(); onDelete() })}
            >
              {t('deckCard.delete')}
            </Button>
          </div>
        )}
      </div>
    </Card>
  )
}
