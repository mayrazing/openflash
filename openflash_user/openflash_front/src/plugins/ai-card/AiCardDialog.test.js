import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const source = readFileSync(new URL('./AiCardDialog.jsx', import.meta.url), 'utf8')

test('AI result dialog follows the space above the live bottom boundary', () => {
  assert.match(source, /useAiDialogViewportStyle\(open\)/)
  assert.match(source, /ResizeObserver/)
  assert.match(source, /style=\{viewportStyle\}/)
  assert.match(source, /!overflow-hidden flex flex-col/)
  assert.match(source, /h-full overflow-y-auto overscroll-contain/)
})
