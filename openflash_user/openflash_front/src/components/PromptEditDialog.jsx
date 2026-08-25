import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, List } from 'konsta/react'
import { withGenericClick } from '../lib/soundEngine'
import KonstaSheetShell from './konsta/KonstaSheetShell'
import ListInput from './konsta/AppListInput'

export default function PromptEditDialog({ open, title, placeholder, value, onChange, onConfirm, onCancel }) {
  const { t } = useTranslation()
  const fieldRef = useRef(null)
  const [height, setHeight] = useState(null)
  const dragState = useRef(null)

  const onDragStart = (e) => {
    e.preventDefault()
    const textarea = fieldRef.current?.querySelector('textarea')
    if (!textarea) return
    const startY = e.touches ? e.touches[0].clientY : e.clientY
    dragState.current = { startY, startHeight: textarea.offsetHeight }

    const onMove = (e) => {
      const currentY = e.touches ? e.touches[0].clientY : e.clientY
      const delta = currentY - dragState.current.startY
      setHeight(Math.max(80, dragState.current.startHeight - delta))
    }
    const onEnd = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onEnd)
      document.removeEventListener('touchmove', onMove)
      document.removeEventListener('touchend', onEnd)
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onEnd)
    document.addEventListener('touchmove', onMove, { passive: false })
    document.addEventListener('touchend', onEnd)
  }

  return (
    <KonstaSheetShell open={open} onClose={onCancel} ariaLabel={title}>
      <div className="mx-auto max-w-lg p-5">
        <p className="mb-3 text-sm font-medium text-app-label-primary">{title}</p>
        {/* 顶部拖动条: 向上拉高, 向下拉低 */}
        <div
          onMouseDown={onDragStart}
          onTouchStart={onDragStart}
          className="flex justify-center items-center h-8 cursor-row-resize select-none -mb-1"
        >
          <div className="h-1 w-10 rounded-full bg-app-control" />
        </div>
        <List inset strong className="!mx-0 !my-0">
          <ListInput
            ref={fieldRef}
            outline
            type="textarea"
            autoFocus
            value={value}
            onChange={(e) => onChange(e.target.value)}
            inputStyle={height ? { height } : undefined}
            inputClassName="!min-h-28 resize-none"
            placeholder={placeholder}
          />
        </List>
        <div className="flex gap-3 mt-2">
          <Button rounded className="app-primary-fill" onClick={withGenericClick(onConfirm)}>{t('common.confirm')}</Button>
          <Button tonal rounded onClick={withGenericClick(onCancel)}>{t('common.cancel')}</Button>
        </div>
      </div>
    </KonstaSheetShell>
  )
}
