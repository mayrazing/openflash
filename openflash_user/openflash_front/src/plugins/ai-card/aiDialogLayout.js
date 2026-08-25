const DIALOG_HEIGHT_RATIO = 0.8

/**
 * 按底部固定操作栏上方的可用空间计算弹框高度和垂直中心。
 */
export function getAiDialogViewportStyle(bottomBoundaryHeight) {
  const boundaryHeight = Math.max(0, Number(bottomBoundaryHeight) || 0)
  return {
    height: `calc(80dvh - ${boundaryHeight * DIALOG_HEIGHT_RATIO}px)`,
    top: `calc(50dvh - ${boundaryHeight / 2}px)`,
  }
}
