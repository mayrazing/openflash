import { Dialog } from 'konsta/react'
import { createPortal } from 'react-dom'
import useModalBodyLock from '../../lib/useModalBodyLock'
import { createDialogEventBoundary } from './dialogEventBoundary'
import useAccessibleModal from './useAccessibleModal'

/**
 * Konsta Dialog 的迁移适配层：继续沿用项目现有的滚动锁，避免移动端弹窗
 * 打开时背景页面跟随手指滚动。
 */
export default function KonstaDialogShell({
  open,
  title,
  children,
  buttons,
  onClose,
  ariaLabel,
  className = '',
  style,
}) {
  useModalBodyLock(open)
  const modalRef = useAccessibleModal(open, onClose)
  const eventBoundary = createDialogEventBoundary(onClose)

  if (!open) return null

  return createPortal((
    <Dialog
      ref={modalRef}
      opened
      title={title}
      buttons={buttons}
      onClick={eventBoundary.onClick}
      onBackdropClick={eventBoundary.onBackdropClick}
      role="dialog"
      aria-modal="true"
      aria-label={ariaLabel ?? (typeof title === 'string' ? title : undefined)}
      tabIndex={-1}
      className={`max-h-[calc(100dvh-var(--app-safe-top)-var(--app-safe-bottom)-2rem)] overflow-y-auto ${className}`}
      style={style}
    >
      {children}
    </Dialog>
  ), document.body)
}
