import { DialogButton } from 'konsta/react'
import { useTranslation } from 'react-i18next'
import { withGenericClick } from '../../lib/soundEngine'
import KonstaDialogShell from './KonstaDialogShell'

export default function KonstaConfirmDialog({
  open,
  message,
  onConfirm,
  onCancel,
  confirmText,
  destructive = true,
  confirmButtonClassName,
}) {
  const { t } = useTranslation()

  return (
    <KonstaDialogShell
      open={open}
      onClose={onCancel}
      ariaLabel={typeof message === 'string' ? message : undefined}
      buttons={(
        <>
          <DialogButton onClick={withGenericClick(onCancel)}>
            {t('confirm.cancel')}
          </DialogButton>
          <DialogButton
            strong
            onClick={withGenericClick(onConfirm)}
            className={confirmButtonClassName ?? (destructive ? 'k-color-brand-danger' : '')}
          >
            {confirmText ?? t('confirm.defaultConfirm')}
          </DialogButton>
        </>
      )}
    >
      <p className="leading-relaxed">{message}</p>
    </KonstaDialogShell>
  )
}
