import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./MarkdownRenderer.jsx', import.meta.url), 'utf8')

test('MarkdownRenderer uses code surface tokens for inline and block code', () => {
  assert.match(source, /<code[\s\S]*?className="rounded bg-app-code-surface[^"]*text-app-code-label"/)
  assert.match(source, /<pre[\s\S]*?className="[^"]*bg-app-code-surface[^"]*text-app-code-label"/)
})

test('MarkdownRenderer uses secondary fill for clickable-item hover states', () => {
  assert.equal((source.match(/hover:bg-app-fill-secondary/g) ?? []).length, 3)
  assert.doesNotMatch(source, /hover:bg-app-selected/)
})
