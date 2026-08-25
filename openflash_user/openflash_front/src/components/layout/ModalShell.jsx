import { withGenericClick } from '../../lib/soundEngine'
import useModalBodyLock from '../../lib/useModalBodyLock'

const ALIGN_CLASS = {
  center: 'items-center',
  bottom: 'items-end sm:items-center',
}

const PANEL_VARIANT_CLASS = {
  dialog: 'rounded-2xl',
  bottomSheet: 'rounded-t-2xl rounded-b-none sm:rounded-2xl',
}

const PANEL_OVERFLOW_CLASS = {
  auto: 'overflow-y-auto',
  hidden: 'overflow-hidden',
}

export function ModalPanel({
  children,
  className = '',
  variant = 'dialog',
  panelOverflow = 'auto',
}) {
  const variantClass = PANEL_VARIANT_CLASS[variant] ?? PANEL_VARIANT_CLASS.dialog
  const overflowClass = PANEL_OVERFLOW_CLASS[panelOverflow] ?? PANEL_OVERFLOW_CLASS.auto
  const maxHeight = variant === 'bottomSheet'
    ? 'calc(100dvh - var(--app-dialog-padding) - var(--app-safe-top) - var(--app-safe-bottom))'
    : 'calc(100dvh - (var(--app-dialog-padding) * 2) - var(--app-safe-top) - var(--app-safe-bottom))'
  return (
    <div
      onClick={(event) => event.stopPropagation()}
      className={`w-full ${overflowClass} ${variantClass} bg-app-surface-primary shadow-xl ${className}`}
      style={{
        maxHeight,
      }}
    >
      {children}
    </div>
  )
}

export default function ModalShell({
  children,
  onClose,
  className = '',
  panelClassName = '',
  align = 'center',
  variant = 'dialog',
  panelOverflow = 'auto',
}) {
  useModalBodyLock(true)
  const isBottomSheet = variant === 'bottomSheet'
  return (
    <div
      onClick={onClose ? withGenericClick(onClose) : undefined}
      className={`fixed inset-0 z-[70] flex ${ALIGN_CLASS[align] ?? ALIGN_CLASS.center} justify-center overflow-y-auto bg-app-overlay ${className}`}
      style={{
        paddingTop: 'calc(var(--app-dialog-padding) + var(--app-safe-top))',
        paddingRight: 'var(--app-page-x)',
        paddingBottom: isBottomSheet ? 'var(--app-safe-bottom)' : 'calc(var(--app-dialog-padding) + var(--app-safe-bottom))',
        paddingLeft: 'var(--app-page-x)',
      }}
    >
      <ModalPanel className={panelClassName} variant={variant} panelOverflow={panelOverflow}>
        {children}
      </ModalPanel>
    </div>
  )
}
