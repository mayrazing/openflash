export function snapshotLockedNextPresentation(item) {
  if (!item?.itemKey || !item?.cardId) return null
  return toLockedNextPresentation(item)
}

function toLockedNextPresentation(item) {
  return {
    itemKey: item.itemKey,
    cardId: item.cardId,
    direction: item.direction,
    kind: item.kind,
    ordinal: item.ordinal ?? 0,
    isNew: !!item.isNew,
    isReview: !!item.isReview,
    isRepractice: !!item.isRepractice,
  }
}

export function cloneLockedNextPresentation(target) {
  if (!target) return null
  return toLockedNextPresentation(target)
}

export function createPendingReplay(sourceItemKey, target) {
  if (!sourceItemKey || !target?.itemKey) return null
  return {
    sourceItemKey,
    target: cloneLockedNextPresentation(target),
  }
}

export function clonePendingReplay(replay) {
  if (!replay) return null
  return {
    sourceItemKey: replay.sourceItemKey,
    target: cloneLockedNextPresentation(replay.target),
  }
}

export function moveItemToNextSlot(queue, currentIndex, targetItemKey) {
  const targetIndex = queue.findIndex((item, index) => index > currentIndex && item?.itemKey === targetItemKey)
  if (targetIndex === -1) return { found: false, queue: [...queue], nextIndex: currentIndex + 1 }
  if (targetIndex === currentIndex + 1) return { found: true, queue: [...queue], nextIndex: currentIndex + 1 }

  const nextQueue = [...queue]
  const [targetItem] = nextQueue.splice(targetIndex, 1)
  nextQueue.splice(currentIndex + 1, 0, targetItem)
  return {
    found: true,
    queue: nextQueue,
    nextIndex: currentIndex + 1,
  }
}

export function removeCurrentReplayOnly(queue, currentIndex) {
  const nextQueue = queue.filter((_, index) => index !== currentIndex)
  return {
    queue: nextQueue,
    nextIndex: currentIndex,
  }
}
