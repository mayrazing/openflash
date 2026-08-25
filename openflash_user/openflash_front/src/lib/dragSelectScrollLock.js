let lockedRoot = null
let savedOverflowY = ''

/**
 * 滑动多选开始时锁住全局滚动容器，防止手指滑过列表项时页面跟着滚。
 */
export function lockRootScrollForDragSelect(root = globalThis.document?.getElementById('root')) {
  if (!root || lockedRoot) return
  lockedRoot = root
  savedOverflowY = root.style.overflowY
  root.style.overflowY = 'hidden'
}

/**
 * 滑动多选结束或中断时恢复全局滚动容器，让页面重新正常上下滑动。
 */
export function unlockRootScrollForDragSelect() {
  if (!lockedRoot) return
  lockedRoot.style.overflowY = savedOverflowY
  lockedRoot = null
  savedOverflowY = ''
}
