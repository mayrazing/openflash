const AUTO_COLLAPSE_SCROLL_TOP = 48

/**
 * 只在收起后仍能保住当前滚动位置时自动收起卡包顶部操作区。
 */
export function shouldAutoCollapseDeckHeader({
  previousScrollTop,
  nextScrollTop,
  scrollHeight,
  clientHeight,
  collapsibleHeight,
}) {
  const maxScrollTopAfterCollapse = scrollHeight - collapsibleHeight - clientHeight
  return nextScrollTop > previousScrollTop
    && nextScrollTop > AUTO_COLLAPSE_SCROLL_TOP
    && maxScrollTopAfterCollapse >= nextScrollTop
}
