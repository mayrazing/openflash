import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createDialogEventBoundary } from './dialogEventBoundary.js'

test('dialog clicks do not reach the owning card', () => {
  const calls = []
  const event = {
    stopPropagation() {
      calls.push('stop')
    },
  }
  const boundary = createDialogEventBoundary(() => calls.push('close'))

  boundary.onClick(event)

  assert.deepEqual(calls, ['stop'])
})

test('backdrop click is stopped before closing the dialog', () => {
  const calls = []
  const event = {
    stopPropagation() {
      calls.push('stop')
    },
  }
  const boundary = createDialogEventBoundary(receivedEvent => {
    assert.equal(receivedEvent, event)
    calls.push('close')
  })

  boundary.onBackdropClick(event)

  assert.deepEqual(calls, ['stop', 'close'])
})
