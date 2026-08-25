import { resolveDefaultDeckId, toggleDefaultDeckId } from './defaultDeckState.js'

export async function syncDefaultDeckAfterDeckLoad(decks, defaultDeckId, deps) {
  const resolved = resolveDefaultDeckId(decks, defaultDeckId)
  if (resolved.shouldClear) {
    await deps.setDefaultDeckId(null)
  }
  return {
    defaultDeckId: resolved.defaultDeckId,
    cleared: resolved.shouldClear,
  }
}

export async function setDefaultDeckFromPopup(currentDefaultDeckId, deckId, deps) {
  if (deckId == null) {
    throw new Error('缺少卡包')
  }
  const nextDefaultDeckId = toggleDefaultDeckId(currentDefaultDeckId, deckId)
  await deps.setDefaultDeckId(nextDefaultDeckId)
  let refreshError = null
  try {
    await deps.refreshMenus()
  } catch (error) {
    refreshError = error
  }
  return { defaultDeckId: nextDefaultDeckId, refreshError }
}
