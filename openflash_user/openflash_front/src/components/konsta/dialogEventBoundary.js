export function createDialogEventBoundary(onBackdropClick) {
  return {
    onClick(event) {
      event.stopPropagation()
    },
    onBackdropClick(event) {
      event.stopPropagation()
      onBackdropClick?.(event)
    },
  }
}
