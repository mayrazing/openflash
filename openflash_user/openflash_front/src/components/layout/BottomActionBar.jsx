const TONE_CLASS = {
  dark: 'bg-app-surface-primary border-t border-app-separator',
  surface: 'bg-app-surface-primary border-t border-app-separator',
}

export default function BottomActionBar({
  children,
  className = '',
  innerClassName = '',
  tone = 'dark',
  dialogBoundary = false,
}) {
  const toneClass = TONE_CLASS[tone] ?? TONE_CLASS.dark
  // 垂直 padding 走 --app-control-y，随紧凑 media 同步缩小，与 --app-bottom-bar-height 的预留高度方向一致
  return (
    <div
      data-dialog-bottom-boundary={dialogBoundary ? 'true' : undefined}
      className={`fixed bottom-0 left-0 right-0 z-50 ${toneClass} px-4 shadow-2xl ${className}`}
      style={{
        paddingTop: 'var(--app-control-y)',
        paddingBottom: 'calc(var(--app-control-y) + var(--app-safe-bottom))',
      }}
    >
      <div className={`mx-auto max-w-lg ${innerClassName}`}>
        {children}
      </div>
    </div>
  )
}
