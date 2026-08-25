import { useTranslation } from 'react-i18next'
import { Button, Card } from 'konsta/react'
import RichEditor from './RichEditor'
import { withGenericClick } from '../lib/soundEngine'

/**
 * 新增卡片表单。adding=false 时显示虚线「＋ 添加卡片」按钮；
 * adding=true 时展开 A/B 面编辑器 + 创建/取消按钮。
 */
export default function NewCardForm({ adding, newFace, createError, onFaceChange, onCreate, onOpen, onCancel }) {
  const { t } = useTranslation()

  return (
    <>
      {adding ? (
        <Card raised outline className="!mx-0 !mb-3 !mt-0">
          <p className="mb-3 text-sm text-app-label-secondary">{t('deckDetail.addCard')}</p>
          <p className="mb-1 text-xs text-app-label-tertiary">{t('common.sideA')}</p>
          <RichEditor key="new-a" initialText={newFace.a.text} initialImages={newFace.a.images} placeholder={t('deckDetail.editorPlaceholder')} onChange={(val) => onFaceChange((p) => ({ ...p, a: val }))} />
          <p className="mb-1 mt-3 text-xs text-app-label-tertiary">{t('common.sideB')}</p>
          <RichEditor key="new-b" initialText={newFace.b.text} initialImages={newFace.b.images} placeholder={t('deckDetail.editorPlaceholder')} onChange={(val) => onFaceChange((p) => ({ ...p, b: val }))} />
          {createError && (
            <p className="mt-3 text-sm text-app-danger">{createError}</p>
          )}
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button rounded className="app-primary-fill" onClick={withGenericClick(onCreate)}>{t('deckDetail.addButton')}</Button>
            <Button tonal rounded onClick={withGenericClick(onCancel)}>{t('common.cancel')}</Button>
          </div>
        </Card>
      ) : (
        <Button large outline rounded onClick={withGenericClick(onOpen)} className="mb-4 border-dashed text-app-accent">
          ＋ {t('deckDetail.addCard')}
        </Button>
      )}
    </>
  )
}
