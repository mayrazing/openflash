import { useTranslation } from 'react-i18next'
import { DialogButton } from 'konsta/react'
import RichEditor from './RichEditor'
import { withGenericClick } from '../lib/soundEngine'
import KonstaDialogShell from './konsta/KonstaDialogShell'

/**
 * 编辑卡片弹窗。editing 为 null 时不渲染。
 * editing: { id, a:{text,images}, b:{text,images} } | null
 */
export default function CardEditModal({ editing, editError, onChange, onSave, onCancel }) {
  const { t } = useTranslation()
  return (
    <KonstaDialogShell
      open={!!editing}
      onClose={onCancel}
      title={t('deckDetail.editCard')}
      className="!w-[min(28rem,calc(100vw-2rem))]"
      buttons={(
        <>
          <DialogButton onClick={withGenericClick(onCancel)}>{t('deckDetail.cancelButton')}</DialogButton>
          <DialogButton strong onClick={withGenericClick(onSave)}>{t('deckDetail.saveButton')}</DialogButton>
        </>
      )}
    >
      {editing && (
        <>
        <p className="mb-1 text-xs text-app-label-tertiary">{t('common.sideA')}</p>
        <RichEditor key={`edit-a-${editing.id}`} initialText={editing.a.text} initialImages={editing.a.images} placeholder={t('deckDetail.editorPlaceholder')} onChange={(val) => onChange((p) => ({ ...p, a: val }))} />
        <p className="mb-1 mt-3 text-xs text-app-label-tertiary">{t('common.sideB')}</p>
        <RichEditor key={`edit-b-${editing.id}`} initialText={editing.b.text} initialImages={editing.b.images} placeholder={t('deckDetail.editorPlaceholder')} onChange={(val) => onChange((p) => ({ ...p, b: val }))} />
        {editError && (<p className="mt-3 text-sm text-app-danger">{editError}</p>)}
        </>
      )}
    </KonstaDialogShell>
  )
}
