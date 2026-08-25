import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./PromptEditDialog.jsx', import.meta.url), 'utf8')

test('PromptEditDialog localizes action buttons', () => {
  assert.match(source, /useTranslation/)
  assert.match(source, /t\('common\.confirm'\)/)
  assert.match(source, /t\('common\.cancel'\)/)
  assert.doesNotMatch(source, />确定</)
  assert.doesNotMatch(source, />取消</)
})

test('PromptEditDialog uses the Konsta textarea field', () => {
  assert.match(source, /ListInput/)
  assert.match(source, /type="textarea"/)
  assert.doesNotMatch(source, /<textarea/)
})

test('PromptEditDialog resizes upward from a handle above the textarea', () => {
  const handleIndex = source.indexOf('onMouseDown={onDragStart}')
  const textareaIndex = source.indexOf('<ListInput')

  assert.ok(handleIndex < textareaIndex)
  assert.match(source, /startHeight - delta/)
})
