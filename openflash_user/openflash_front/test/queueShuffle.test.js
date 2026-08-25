import test from 'node:test'
import assert from 'node:assert/strict'

import {
  reorderTailAvoidingAdjacentCards,
  shuffleAvoidingAdjacentCards,
} from '../src/lib/queueShuffle.js'

function item(cardId, ordinal = 0) {
  return {
    itemKey: `${cardId}:a2b:retry:${ordinal}`,
    cardId,
    direction: 'a2b',
    kind: 'retry',
    ordinal,
    isRepractice: true,
  }
}

function hasAdjacentDuplicate(items, previousCardId = null) {
  return items.some((entry, index) => {
    const previousId = index === 0 ? previousCardId : items[index - 1]?.cardId
    return entry.cardId === previousId
  })
}

test('shuffleAvoidingAdjacentCards avoids adjacent duplicates with more than two cards', () => {
  const queue = [
    item(1, 0), item(1, 1), item(1, 2),
    item(2, 0), item(2, 1), item(2, 2),
    item(3, 0), item(3, 1), item(3, 2),
  ]

  const result = shuffleAvoidingAdjacentCards(queue)

  assert.equal(result.length, queue.length)
  assert.equal(hasAdjacentDuplicate(result), false)
  assert.deepEqual(
    result.map(entry => entry.itemKey).sort(),
    queue.map(entry => entry.itemKey).sort()
  )
})

test('shuffleAvoidingAdjacentCards respects previous card when arranging first item', () => {
  const queue = [
    item(1, 0), item(1, 1),
    item(2, 0), item(2, 1),
    item(3, 0), item(3, 1),
  ]

  const result = shuffleAvoidingAdjacentCards(queue, 1)

  assert.notEqual(result[0].cardId, 1)
  assert.equal(hasAdjacentDuplicate(result, 1), false)
})

test('reorderTailAvoidingAdjacentCards keeps head fixed and reorders only tail', () => {
  const queue = [
    item(9, 0),
    item(8, 0),
    item(1, 0), item(1, 1),
    item(2, 0), item(2, 1),
    item(3, 0), item(3, 1),
  ]

  const result = reorderTailAvoidingAdjacentCards(queue, 2)

  assert.deepEqual(result.slice(0, 2).map(entry => entry.itemKey), ['9:a2b:retry:0', '8:a2b:retry:0'])
  assert.equal(result.length, queue.length)
  assert.equal(hasAdjacentDuplicate(result.slice(2), result[1].cardId), false)
  assert.deepEqual(
    result.slice(2).map(entry => entry.itemKey).sort(),
    queue.slice(2).map(entry => entry.itemKey).sort()
  )
})
