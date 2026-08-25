import { Sheet } from 'konsta/react'
import useModalBodyLock from '../../lib/useModalBodyLock'
import useAccessibleModal from './useAccessibleModal'

export default function KonstaSheetShell({ open, onClose, children, ariaLabel, className = '' }) {
  useModalBodyLock(open)
  const modalRef = useAccessibleModal(open, onClose)

  if (!open) return null

  return (
    <Sheet
      ref={modalRef}
      opened
      onBackdropClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={ariaLabel}
      tabIndex={-1}
      className={`max-h-[calc(100dvh-var(--app-safe-top)-var(--app-safe-bottom))] overflow-y-auto pb-[var(--app-safe-bottom)] ${className}`}
    >
      {children}
    </Sheet>
  )
}
