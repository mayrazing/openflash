import { useTranslation } from 'react-i18next'
import { Button, Card } from 'konsta/react'
import TextWithBreaks from './TextWithBreaks'
import RichFaceContent from './RichFaceContent'
import { getToday, shouldCardRepeatToday } from '../db/database'
import PluginSlot from '../plugins/pluginSlot'
import { usePluginActionSlotState } from '../plugins/usePluginActionSlot'
import { withGenericClick } from '../lib/soundEngine'
import { hasImageTokens, stripImageTokens } from '../lib/richFaceOrder'

function formatNextReview(card, t) {
  if (card.state === 'new') return null
  if (card.state === 'mastered') return null
  if (card.state === 'graduated') return null
  const dateStr = card.fsrs?.nextReviewDate
  if (!dateStr) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const next = new Date(dateStr)
  next.setHours(0, 0, 0, 0)
  const diff = Math.round((next - today) / 86400000)
  if (diff < 0) return { kind: 'overdue', label: t('cardItem.overdue'), color: 'text-app-due-overdue' }
  if (diff === 0) {
    const todayStr = getToday()
    if (shouldCardRepeatToday(card, todayStr)) {
      return { kind: 'today', label: t('cardItem.todayReview'), color: 'text-app-due-today' }
    }
    if (card.fsrs?.lastReviewDate === todayStr) {
      return { kind: 'tomorrow', label: t('cardItem.tomorrowReview'), color: 'text-app-due-tomorrow' }
    }
    return { kind: 'today', label: t('cardItem.todayReview'), color: 'text-app-due-today' }
  }
  if (diff === 1) return { kind: 'tomorrow', label: t('cardItem.tomorrowReview'), color: 'text-app-due-tomorrow' }
  return { kind: 'daysLater', label: t('cardItem.daysLater', { count: diff }), color: 'text-app-label-tertiary' }
}

export default function CardItem({ card, onEdit, onDelete, onReset, isSelectMode = false, selected = false, onToggleSelect, deckId = null }) {
  const { t } = useTranslation()
  const nextReview = formatNextReview(card, t)
  const todayStr = getToday()
  const reviewedToday = card.state !== 'graduated' && card.fsrs?.lastReviewDate === todayStr
  const markedFamiliar = card.state === 'mastered'
  const sideAHasImageOrder = hasImageTokens(card.sideA)
  const sideBHasImageOrder = hasImageTokens(card.sideB)
  const sideAActionText = stripImageTokens(card.sideA)
  const sideBActionText = stripImageTokens(card.sideB)
  const { loaded: openActionsLoaded, actions: openActions } = usePluginActionSlotState('card.open-actions', {
    card,
    title: sideAActionText || t('deckDetail.cardDetail'),
    deckId,
  }, deckId)
  const openCard = openActions[0]?.onOpen
  const canOpenCard = openActionsLoaded && Boolean(openCard)
  const isInteractive = isSelectMode || canOpenCard

  function handleKeyDown(event) {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    if (isSelectMode) {
      onToggleSelect?.()
    } else {
      openCard?.(event)
    }
  }

  return (
    <Card
      raised
      outline
      contentWrapPadding="p-4"
      role={isInteractive ? 'button' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
      aria-pressed={isSelectMode ? selected : undefined}
      onClick={isSelectMode ? onToggleSelect : (canOpenCard ? openCard : undefined)}
      onKeyDown={isInteractive ? handleKeyDown : undefined}
      className={`!mx-0 !my-0 w-full text-left transition-all ${
        isSelectMode
          ? selected
            ? 'border-app-selected-border bg-app-selected ring-2 ring-app-selected-border'
            : ''
          : canOpenCard
            ? 'hover:border-app-control hover:bg-app-fill-secondary'
            : ''
      }`}
    >
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1 min-w-0">
            {sideAHasImageOrder ? (
              <RichFaceContent
                text={card.sideA}
                images={card.sideAImage}
                className="inline-flex flex-wrap items-center gap-1 min-w-0"
                textClassName="text-base font-medium text-app-label-primary break-words [overflow-wrap:anywhere] min-w-0"
                imageClassName="h-14 w-auto rounded-md object-cover"
              />
            ) : (
              <TextWithBreaks text={card.sideA} className="text-base font-medium text-app-label-primary break-words [overflow-wrap:anywhere] min-w-0" />
            )}
            <PluginSlot slotName="card.actions" props={{ text: sideAActionText, size: 'sm', className: 'ml-1 shrink-0', deckId }} deckId={deckId} />
          </div>
          {!sideAHasImageOrder && card.sideAImage?.length > 0 && (
            <div className="flex gap-1 mt-1 flex-wrap">
              {card.sideAImage.map((src, i) => (
                <img key={i} src={src} alt="" className="h-14 w-auto rounded-md object-cover" />
              ))}
            </div>
          )}
          <div className="flex items-center gap-1 min-w-0 mt-0.5">
            {sideBHasImageOrder ? (
              <RichFaceContent
                text={card.sideB}
                images={card.sideBImage}
                className="inline-flex flex-wrap items-center gap-1 min-w-0"
                textClassName="text-sm text-app-label-secondary break-words [overflow-wrap:anywhere] min-w-0"
                imageClassName="h-14 w-auto rounded-md object-cover"
              />
            ) : (
              <TextWithBreaks text={card.sideB} className="text-sm text-app-label-secondary break-words [overflow-wrap:anywhere] min-w-0" />
            )}
            <PluginSlot slotName="card.actions" props={{ text: sideBActionText, size: 'sm', className: 'ml-1 shrink-0', deckId }} deckId={deckId} />
          </div>
          {!sideBHasImageOrder && card.sideBImage?.length > 0 && (
            <div className="flex gap-1 mt-1 flex-wrap">
              {card.sideBImage.map((src, i) => (
                <img key={i} src={src} alt="" className="h-14 w-auto rounded-md object-cover" />
              ))}
            </div>
          )}
          {(nextReview || reviewedToday || markedFamiliar) && (
            <div className="flex items-center gap-2 mt-1">
              {nextReview && <span className={`text-xs ${nextReview.color}`}>{nextReview.kind === 'overdue' && reviewedToday ? t('cardItem.backlogSuffix') : nextReview.label}</span>}
              {markedFamiliar && <span className="text-xs text-app-familiar">{t('cardItem.familiar')}</span>}
              {reviewedToday && <span className="text-xs text-app-success">{t('cardItem.reviewedToday')}</span>}
            </div>
          )}
        </div>
        <div className="flex gap-1 shrink-0">
          {isSelectMode ? (
            selected && (
              <div className="w-5 h-5 rounded-full bg-app-accent-fill flex items-center justify-center text-app-on-accent text-xs flex-shrink-0">✓</div>
            )
          ) : (
            <>
              <Button inline small clear rounded onClick={withGenericClick((event) => { event.stopPropagation(); onEdit() })}>{t('cardItem.edit')}</Button>
              {card.state !== 'new' && (
                <Button inline small clear rounded className="text-app-warning" onClick={withGenericClick((event) => { event.stopPropagation(); onReset() })}>
                  {t('cardItem.reset')}
                </Button>
              )}
              <Button inline small clear rounded className="text-app-danger" onClick={withGenericClick((event) => { event.stopPropagation(); onDelete() })}>{t('cardItem.delete')}</Button>
            </>
          )}
        </div>
      </div>
    </Card>
  )
}
