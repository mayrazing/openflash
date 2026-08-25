export const EMPTY_FACE = { text: '', images: [] }
export const EMPTY_STATS = {
  total: null,
  new: null,
  learning: null,
  tomorrow: null,
  today: null,
  backlog: null,
  newPaused: false,
}
export const PAGE_SIZE = 50
export const DEFERRED_FILTERS = new Set(['today', 'tomorrow'])
export const LOAD_ALL_STATUS = {
  BUSY: 'busy',
  ALREADY_DONE: 'already-done',
  LOADED: 'loaded',
  STALE_OR_ERROR: 'stale-or-error',
}

export function isDeferredFilter(filter) {
  return DEFERRED_FILTERS.has(filter)
}

export function toApiState(filter) {
  return isDeferredFilter(filter) ? null : filter
}

/**
 * “今天”和“明天”列表按复习时间排序。
 */
export function compareTodayCards(a, b, today) {
  const aMastered = a.state === 'mastered' ? 1 : 0
  const bMastered = b.state === 'mastered' ? 1 : 0
  if (aMastered !== bMastered) return aMastered - bMastered

  const aDate = a.fsrs?.nextReviewDate
  const bDate = b.fsrs?.nextReviewDate

  if (!aDate && !bDate) return 0
  if (!aDate) return 1
  if (!bDate) return -1

  if (aDate !== bDate) return aDate.localeCompare(bDate)

  const aReviewedToday = a.fsrs?.lastReviewDate === today ? 1 : 0
  const bReviewedToday = b.fsrs?.lastReviewDate === today ? 1 : 0
  if (aReviewedToday !== bReviewedToday) return bReviewedToday - aReviewedToday

  return (a.createdAt ?? '').localeCompare(b.createdAt ?? '')
}

/**
 * 按详情页当前搜索词过滤卡片，供列表展示和“全选所有”共用同一口径。
 */
export function filterCardsByKeyword(cards, keyword) {
  const kw = (keyword ?? '').trim().toLowerCase()
  if (!kw) return cards
  return cards.filter(card => (
    (card.sideA ?? '').toLowerCase().includes(kw)
    || (card.sideB ?? '').toLowerCase().includes(kw)
  ))
}

/**
 * 统计数字还没回来时，先给个占位。
 */
export function formatStatCount(count) {
  return count == null ? '...' : count
}

/**
 * 用后端返回的新卡片替换当前列表里的旧卡片。
 */
export function replaceCardInLoadedList(cards, nextCard) {
  if (!Array.isArray(cards) || !nextCard?.id) return cards
  let found = false
  const nextCards = cards.map(card => {
    if (String(card.id) !== String(nextCard.id)) return card
    found = true
    return nextCard
  })
  return found ? nextCards : cards
}

/**
 * 把批量导入文本拆成卡片，并保留格式错误行用于结果页展示。
 */
export function parseCardTextRows(text, t) {
  const cards = []
  const failures = []
  for (const line of text.split('\n')) {
    const trimmed = line.trim()
    if (!trimmed) continue
    const commaIdx = trimmed.indexOf(',')
    if (commaIdx === -1) {
      failures.push({ sideA: trimmed, sideB: '', reason: t('deckDetail.missingComma') })
      continue
    }
    const sideA = trimmed.slice(0, commaIdx).trim().replace(/\\n/g, '\n')
    const sideB = trimmed.slice(commaIdx + 1).trim().replace(/\\n/g, '\n')
    if (!sideA || !sideB) {
      failures.push({ sideA, sideB, reason: t('deckDetail.bothSidesRequired') })
      continue
    }
    cards.push({ sideA, sideB })
  }
  return { cards, invalidCount: failures.length, failures }
}

/**
 * 计算一次拖拽划选后的新选中集合。
 * 把锚点到当前手指所在项的连续区间，按 toggle 语义叠加到开始时的快照上。
 * 纯函数：不碰 DOM、不依赖 React，便于单测。两页 updateDragSelect 共用。
 *
 * @param {Object} p
 * @param {Array}  p.items            当前显示列表
 * @param {Function} p.getId          item => id
 * @param {number} p.anchorIndex      手势起点在 items 中的下标
 * @param {number} p.currentIndex     手指当前所在项的下标
 * @param {Set<string>} p.snapshot    手势开始时的选中快照（string id）
 * @param {string|null} p.preToggledAnchor 已在按下时预先 toggle 过的锚点 id，区间内跳过它
 * @returns {string[]} 新的选中 id 列表
 */
export function computeRangeSelection({ items, getId, anchorIndex, currentIndex, snapshot, preToggledAnchor }) {
  const min = Math.min(anchorIndex, currentIndex)
  const max = Math.max(anchorIndex, currentIndex)
  const rangeIds = new Set(items.slice(min, max + 1).map(it => String(getId(it))))
  const next = new Set(snapshot)
  rangeIds.forEach(id => {
    if (id === preToggledAnchor) return
    if (next.has(id)) next.delete(id)
    else next.add(id)
  })
  return [...next]
}
