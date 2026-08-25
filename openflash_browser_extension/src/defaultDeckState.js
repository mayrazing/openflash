export function normalizeDeckId(deckId) {
  return deckId == null ? null : String(deckId)
}

export function deckExists(decks, deckId) {
  const normalizedDeckId = normalizeDeckId(deckId)
  return Boolean(normalizedDeckId) && decks.some((deck) => String(deck.id) === normalizedDeckId)
}

export function resolveDefaultDeckId(decks, defaultDeckId) {
  const normalizedDefaultDeckId = normalizeDeckId(defaultDeckId)
  if (!normalizedDefaultDeckId) {
    return { defaultDeckId: null, shouldClear: false }
  }
  if (deckExists(decks, normalizedDefaultDeckId)) {
    return { defaultDeckId: normalizedDefaultDeckId, shouldClear: false }
  }
  return { defaultDeckId: null, shouldClear: true }
}

export function toggleDefaultDeckId(currentDefaultDeckId, deckId) {
  const normalizedCurrent = normalizeDeckId(currentDefaultDeckId)
  const normalizedDeckId = normalizeDeckId(deckId)
  return normalizedCurrent === normalizedDeckId ? null : normalizedDeckId
}

export function buildDeckRows(decks, selectedDeckId, defaultDeckId) {
  const normalizedSelectedDeckId = normalizeDeckId(selectedDeckId)
  const normalizedDefaultDeckId = normalizeDeckId(defaultDeckId)
  return decks.map((deck) => ({
    id: String(deck.id),
    name: deck.name,
    selected: String(deck.id) === normalizedSelectedDeckId,
    defaultDeck: String(deck.id) === normalizedDefaultDeckId,
  }))
}
