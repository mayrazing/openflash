import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildDeckRows,
  resolveDefaultDeckId,
  toggleDefaultDeckId,
} from '../src/defaultDeckState.js'

const decks = [
  { id: 1, name: 'A' },
  { id: 2, name: 'B' },
]

test('resolveDefaultDeckId keeps existing default deck', () => {
  assert.deepEqual(resolveDefaultDeckId(decks, '2'), {
    defaultDeckId: '2',
    shouldClear: false,
  })
})

test('resolveDefaultDeckId clears missing default deck', () => {
  assert.deepEqual(resolveDefaultDeckId(decks, '9'), {
    defaultDeckId: null,
    shouldClear: true,
  })
})

test('resolveDefaultDeckId keeps empty default deck empty without cleanup', () => {
  assert.deepEqual(resolveDefaultDeckId(decks, null), {
    defaultDeckId: null,
    shouldClear: false,
  })
})

test('toggleDefaultDeckId sets a new default deck', () => {
  assert.equal(toggleDefaultDeckId('1', '2'), '2')
})

test('toggleDefaultDeckId clears current default deck', () => {
  assert.equal(toggleDefaultDeckId('2', '2'), null)
})

test('buildDeckRows marks selected and default independently', () => {
  assert.deepEqual(buildDeckRows(decks, '1', '2'), [
    { id: '1', name: 'A', selected: true, defaultDeck: false },
    { id: '2', name: 'B', selected: false, defaultDeck: true },
  ])
})
