import test from 'node:test'
import assert from 'node:assert/strict'
import { getAiDialogViewportStyle } from './aiDialogLayout.js'

test('AI dialog uses 80 percent of the space above the bottom boundary', () => {
  assert.deepEqual(getAiDialogViewportStyle(126.5), {
    height: 'calc(80dvh - 101.2px)',
    top: 'calc(50dvh - 63.25px)',
  })
})

test('AI dialog keeps the original viewport layout without a bottom boundary', () => {
  assert.deepEqual(getAiDialogViewportStyle(0), {
    height: 'calc(80dvh - 0px)',
    top: 'calc(50dvh - 0px)',
  })
})
