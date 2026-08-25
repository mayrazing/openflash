import test from 'node:test'
import assert from 'node:assert/strict'
import { lockRootScrollForDragSelect, unlockRootScrollForDragSelect } from './dragSelectScrollLock.js'

test('drag select scroll lock freezes root scroll and restores previous inline overflow', () => {
  const root = { style: { overflowY: 'scroll' } }

  lockRootScrollForDragSelect(root)
  assert.equal(root.style.overflowY, 'hidden')

  unlockRootScrollForDragSelect()
  assert.equal(root.style.overflowY, 'scroll')
})

test('drag select scroll lock uses document root when no root is passed', () => {
  const root = { style: { overflowY: '' } }
  const previousDocument = globalThis.document
  globalThis.document = {
    getElementById: (id) => (id === 'root' ? root : null),
  }

  try {
    lockRootScrollForDragSelect()
    assert.equal(root.style.overflowY, 'hidden')
    unlockRootScrollForDragSelect()
    assert.equal(root.style.overflowY, '')
  } finally {
    globalThis.document = previousDocument
  }
})
