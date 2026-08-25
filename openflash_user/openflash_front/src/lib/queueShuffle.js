export function shuffleAvoidingAdjacentCards(items, previousCardId = null) {
  const groups = groupShuffledByCard(items)
  const result = []
  let lastCardId = previousCardId

  while (groups.size > 0) {
    const candidates = [...groups.entries()].filter(([cardId]) => cardId !== String(lastCardId))
    const pool = candidates.length > 0 ? candidates : [...groups.entries()]
    const maxCount = Math.max(...pool.map(([, group]) => group.length))
    const best = pool.filter(([, group]) => group.length === maxCount)
    const [cardId, group] = best[Math.floor(Math.random() * best.length)]
    const nextItem = group.shift()
    result.push(nextItem)
    lastCardId = nextItem.cardId
    if (group.length === 0) groups.delete(cardId)
  }

  return result
}

export function reorderTailAvoidingAdjacentCards(queue, startIndex) {
  const safeStart = Math.max(0, Math.min(startIndex, queue.length))
  const head = queue.slice(0, safeStart)
  const previousCardId = head.length > 0 ? head[head.length - 1].cardId : null
  const tail = shuffleAvoidingAdjacentCards(queue.slice(safeStart), previousCardId)
  return [...head, ...tail]
}

function groupShuffledByCard(items) {
  const groups = new Map()
  for (const item of items ?? []) {
    const cardId = String(item.cardId)
    if (!groups.has(cardId)) groups.set(cardId, [])
    groups.get(cardId).push(item)
  }
  for (const group of groups.values()) {
    shuffleInPlace(group)
  }
  return groups
}

function shuffleInPlace(items) {
  for (let i = items.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[items[i], items[j]] = [items[j], items[i]]
  }
}
