import test from 'node:test'
import assert from 'node:assert/strict'

import {
  cloneLockedNextPresentation,
  createPendingReplay,
  moveItemToNextSlot,
  removeCurrentReplayOnly,
  snapshotLockedNextPresentation,
} from '../src/lib/practiceReplay.js'

function createItem(itemKey, extra = {}) {
  const [cardId, direction, kind, ordinal] = itemKey.split(':')
  return {
    itemKey,
    cardId: Number(cardId),
    direction,
    kind,
    ordinal: Number(ordinal),
    isNew: false,
    isReview: true,
    isRepractice: kind !== 'base',
    ...extra,
  }
}

test('snapshotLockedNextPresentation only keeps replay schema fields', () => {
  const snapshot = snapshotLockedNextPresentation({
    ...createItem('1:a2b:retry:2'),
    replayOnly: true,
    extra: 'ignored',
  })

  assert.deepEqual(snapshot, {
    itemKey: '1:a2b:retry:2',
    cardId: 1,
    direction: 'a2b',
    kind: 'retry',
    ordinal: 2,
    isNew: false,
    isReview: true,
    isRepractice: true,
  })
  assert.equal(cloneLockedNextPresentation(snapshot).itemKey, '1:a2b:retry:2')
})

test('moveItemToNextSlot moves existing target to next slot without dropping others', () => {
  const queue = [
    createItem('1:a2b:base:0'),
    createItem('2:a2b:base:0'),
    createItem('3:a2b:base:0'),
  ]

  const result = moveItemToNextSlot(queue, 0, '3:a2b:base:0')

  assert.equal(result.found, true)
  assert.deepEqual(result.queue.map(item => item.itemKey), [
    '1:a2b:base:0',
    '3:a2b:base:0',
    '2:a2b:base:0',
  ])
  assert.equal(result.nextIndex, 1)
})

test('removeCurrentReplayOnly keeps cursor on same index for the real queue', () => {
  const queue = [
    createItem('1:a2b:base:0'),
    { ...createItem('9:a2b:retry:0'), replayOnly: true },
    createItem('2:a2b:base:0'),
  ]

  const result = removeCurrentReplayOnly(queue, 1)

  assert.deepEqual(result.queue.map(item => item.itemKey), [
    '1:a2b:base:0',
    '2:a2b:base:0',
  ])
  assert.equal(result.nextIndex, 1)
})

test('createPendingReplay clones target without leaking object identity', () => {
  const target = createItem('7:a2b:retry:1')
  const replay = createPendingReplay('1:a2b:base:0', target)

  assert.equal(replay.sourceItemKey, '1:a2b:base:0')
  assert.deepEqual(replay.target, snapshotLockedNextPresentation(target))
  assert.notEqual(replay.target, target)
})
