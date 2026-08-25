import KonstaConfirmDialog from './konsta/KonstaConfirmDialog'

export default function ConfirmDialog({
    open,
    message,
    onConfirm,
    onCancel,
    confirmText,
    confirmButtonClassName = 'bg-app-danger-fill hover:bg-app-danger-hover active:bg-app-danger-pressed text-app-on-danger disabled:bg-app-disabled-fill disabled:text-app-disabled-label',
}) {
    return (
        <KonstaConfirmDialog
            open={open}
            message={message}
            onConfirm={onConfirm}
            onCancel={onCancel}
            confirmText={confirmText}
            destructive={confirmButtonClassName.includes('danger') || confirmButtonClassName.includes('red')}
            confirmButtonClassName={confirmButtonClassName}
        />
    )
}
