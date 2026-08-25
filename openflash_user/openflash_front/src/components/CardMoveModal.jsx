import { useTranslation } from 'react-i18next'
import { DialogButton, List } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'
import KonstaDialogShell from './konsta/KonstaDialogShell'
import ListInput from './konsta/AppListInput'

/**
 * 批量迁移卡片时选择目标卡包并展示上次迁移结果。
 */
export default function CardMoveModal({
  open,
  decks,
  selectedTargetDeckId,
  selectedCount,
  moving,
  moveResult,
  error,
  onTargetChange,
  onConfirm,
  onClose,
}) {
  const { t } = useTranslation()
  return (
    <KonstaDialogShell
      open={open}
      onClose={onClose}
      title={t('deckDetail.moveCardsTitle')}
      className="!w-[min(28rem,calc(100vw-2rem))]"
      buttons={(
        <>
          <DialogButton onClick={withGenericClick(onClose)} disabled={moving}>
            {moveResult ? t('common.close') : t('common.cancel')}
          </DialogButton>
          {!moveResult && (
            <DialogButton
              strong
              onClick={withGenericClick(onConfirm)}
              disabled={moving || !selectedTargetDeckId || selectedCount === 0}
            >
              {moving ? t('deckDetail.moveCardsMoving') : t('deckDetail.moveCardsConfirm')}
            </DialogButton>
          )}
        </>
      )}
    >
        <p className="mb-3 text-sm opacity-65">
          {t('deckDetail.moveCardsDesc', { count: selectedCount })}
        </p>
        <List className="!my-0">
          <ListInput
            type="select"
            outline
            dropdown
            value={selectedTargetDeckId ?? ''}
            onChange={(event) => onTargetChange(event.target.value)}
            disabled={moving}
          >
            <option value="">{t('deckDetail.moveCardsSelectPlaceholder')}</option>
            {decks.map(deck => (
              <option key={deck.id} value={deck.id}>{deck.name}</option>
            ))}
          </ListInput>
        </List>
        {moveResult && (
          <div className="mb-3 rounded-2xl bg-app-fill-secondary px-3 py-2 text-sm">
            {t('deckDetail.moveCardsResult', {
              moved: moveResult.movedCount ?? 0,
              duplicate: moveResult.duplicateCount ?? 0,
              invalid: moveResult.invalidCount ?? 0,
            })}
          </div>
        )}
        {error && <p className="mb-3 text-sm text-app-danger">{error}</p>}
    </KonstaDialogShell>
  )
}
