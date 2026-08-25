import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_CARD_SORT_ORDER,
  getDeckCardSortOrder,
  normalizeCardSortOrder,
  saveDeckCardSortOrder,
} from './deckCardSortPreference.js'

const originalLocalStorage = globalThis.localStorage

afterEach(() => {
  globalThis.localStorage = originalLocalStorage
})

test('deck card sort preference stores supported values per deck', () => {
  const store = new Map()
  globalThis.localStorage = {
    getItem: (key) => store.get(key) ?? null,
    setItem: (key, value) => { store.set(key, value) },
  }

  assert.equal(getDeckCardSortOrder(42), DEFAULT_CARD_SORT_ORDER)
  saveDeckCardSortOrder(42, 'created_asc')

  assert.equal(getDeckCardSortOrder(42), 'created_asc')
  assert.equal(getDeckCardSortOrder(7), DEFAULT_CARD_SORT_ORDER)
})

test('deck card sort preference falls back for unsupported values', () => {
  assert.equal(normalizeCardSortOrder('created_asc'), 'created_asc')
  assert.equal(normalizeCardSortOrder('bad'), DEFAULT_CARD_SORT_ORDER)
  assert.equal(saveDeckCardSortOrder(42, 'bad'), DEFAULT_CARD_SORT_ORDER)
})
