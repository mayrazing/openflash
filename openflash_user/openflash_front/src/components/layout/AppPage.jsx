const BOTTOM_INSET_CLASS = {
  none: '',
  selection: 'pb-[calc(var(--app-bottom-bar-height)+var(--app-page-y))]',
}

export default function AppPage({
  children,
  className = '',
  contentClassName = '',
  bottomInset = 'none',
  style,
  ...mainProps
}) {
  const insetClass = BOTTOM_INSET_CLASS[bottomInset] ?? BOTTOM_INSET_CLASS.none
  return (
    <main {...mainProps} className={`min-h-full bg-app-background ${className}`} style={style}>
      <div
        className={`max-w-lg mx-auto ${insetClass} ${contentClassName}`}
        style={{
          paddingLeft: 'var(--app-page-x)',
          paddingRight: 'var(--app-page-x)',
          paddingTop: 'var(--app-page-y)',
          paddingBottom: bottomInset === 'none' ? 'var(--app-page-y)' : undefined,
        }}
      >
        {children}
      </div>
    </main>
  )
}
