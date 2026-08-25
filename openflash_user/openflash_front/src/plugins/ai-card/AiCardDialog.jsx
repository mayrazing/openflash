import { useLayoutEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DialogButton } from 'konsta/react'
import MarkdownRenderer from '../../components/MarkdownRenderer'
import PluginSlot from '../pluginSlot'
import { withGenericClick } from '../../lib/soundEngine'
import KonstaDialogShell from '../../components/konsta/KonstaDialogShell'
import { getAiDialogViewportStyle } from './aiDialogLayout.js'

const DIALOG_BOTTOM_BOUNDARY_SELECTOR = '[data-dialog-bottom-boundary="true"]'

/**
 * 跟随当前底部固定操作栏高度，给弹框保留不会被遮挡的空间。
 */
function useAiDialogViewportStyle(open) {
  const [bottomBoundaryHeight, setBottomBoundaryHeight] = useState(0)

  useLayoutEffect(() => {
    if (!open) return undefined
    const boundary = document.querySelector(DIALOG_BOTTOM_BOUNDARY_SELECTOR)
    if (!boundary) {
      setBottomBoundaryHeight(0)
      return undefined
    }

    function updateBoundaryHeight() {
      setBottomBoundaryHeight(boundary.getBoundingClientRect().height)
    }

    updateBoundaryHeight()
    if (typeof ResizeObserver === 'undefined') return undefined

    const observer = new ResizeObserver(updateBoundaryHeight)
    observer.observe(boundary, { box: 'border-box' })
    return () => observer.disconnect()
  }, [open])

  return getAiDialogViewportStyle(bottomBoundaryHeight)
}

export default function AiCardDialog({
  open,
  title,
  markdown,
  deckId,
  onRegenerate,
  onClose,
}) {
  const { t } = useTranslation()
  const viewportStyle = useAiDialogViewportStyle(open)

  if (!open) return null

  return (
    <KonstaDialogShell
      open={open}
      onClose={onClose}
      ariaLabel={title}
      className="!w-[min(36rem,calc(100vw-2rem))] !overflow-hidden flex flex-col [&>:first-child]:min-h-0 [&>:first-child]:flex-1 [&>:first-child]:justify-start [&>:first-child>:last-child]:min-h-0 [&>:first-child>:last-child]:flex-1 [&>:first-child>:last-child]:overflow-hidden [&>:last-child]:shrink-0"
      style={viewportStyle}
      title={(
        <div className="min-w-0 text-left">
          <p className="text-xs font-normal text-app-label-tertiary">{t('aiDialog.result')}</p>
          <div className="flex min-w-0 items-center gap-1">
            <h2 className="truncate">{title}</h2>
            <PluginSlot
              slotName="card.actions"
              props={{ text: title, size: 'sm', className: 'shrink-0', deckId }}
              deckId={deckId}
            />
          </div>
        </div>
      )}
      buttons={(
        <>
          {onRegenerate && (
            <DialogButton strong onClick={withGenericClick(onRegenerate)}>
              {t('aiDialog.regenerate')}
            </DialogButton>
          )}
          <DialogButton onClick={withGenericClick(onClose)}>
            {t('aiDialog.close')}
          </DialogButton>
        </>
      )}
    >
      <div className="h-full overflow-y-auto overscroll-contain">
        <MarkdownRenderer markdown={markdown} />
      </div>
    </KonstaDialogShell>
  )
}
