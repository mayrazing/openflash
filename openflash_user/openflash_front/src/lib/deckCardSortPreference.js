export const DEFAULT_CARD_SORT_ORDER = 'created_desc'
export const CARD_SORT_ORDERS = ['created_desc', 'created_asc']

function storageKey(deckId) {
  return `deck:${deckId}:card-sort-order`
}

/**
 * 把卡片排序值限制到页面支持的两个选项。
 */
export function normalizeCardSortOrder(value) {
  return CARD_SORT_ORDERS.includes(value) ? value : DEFAULT_CARD_SORT_ORDER
}

/**
 * 读取某个卡包在当前浏览器里的卡片排序偏好。
 */
export function getDeckCardSortOrder(deckId) {
  if (!deckId || typeof localStorage === 'undefined') return DEFAULT_CARD_SORT_ORDER
  try {
    return normalizeCardSortOrder(localStorage.getItem(storageKey(deckId)))
  } catch {
    return DEFAULT_CARD_SORT_ORDER
  }
}

/**
 * 保存某个卡包在当前浏览器里的卡片排序偏好。
 */
export function saveDeckCardSortOrder(deckId, value) {
  const nextValue = normalizeCardSortOrder(value)
  if (!deckId || typeof localStorage === 'undefined') return nextValue
  try {
    localStorage.setItem(storageKey(deckId), nextValue)
  } catch {
    // localStorage 不可写时只让当前页面状态生效。
  }
  return nextValue
}
