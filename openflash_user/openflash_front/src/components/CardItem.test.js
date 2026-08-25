import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

test('CardItem does not render next-review text for graduated cards', () => {
  const source = readFileSync(join(__dirname, 'CardItem.jsx'), 'utf8')

  assert.match(source, /card\.state === 'graduated'/)
})

test('CardItem waits for plugin action loading before exposing card open affordance', () => {
  const source = readFileSync(join(__dirname, 'CardItem.jsx'), 'utf8')

  assert.match(source, /usePluginActionSlotState\('card\.open-actions'/)
  assert.match(source, /const\s+canOpenCard\s*=\s*openActionsLoaded\s*&&\s*Boolean\(openCard\)/)
  assert.match(source, /role=\{isInteractive\s*\?\s*'button'\s*:\s*undefined\}/)
  assert.match(source, /tabIndex=\{isInteractive\s*\?\s*0\s*:\s*undefined\}/)
  assert.match(source, /onClick=\{isSelectMode\s*\?\s*onToggleSelect\s*:\s*\(canOpenCard\s*\?\s*openCard\s*:\s*undefined\)\}/)
  assert.match(source, /onKeyDown=\{isInteractive\s*\?\s*handleKeyDown\s*:\s*undefined\}/)
})

test('CardItem keeps hover and selected borders semantically distinct', () => {
  const source = readFileSync(join(__dirname, 'CardItem.jsx'), 'utf8')

  assert.match(source, /hover:border-app-control/)
  assert.match(source, /hover:bg-app-fill-secondary/)
  assert.match(source, /border-app-selected-border bg-app-selected ring-2 ring-app-selected-border/)
  assert.doesNotMatch(source, /hover:border-app-selected-border/)
  assert.doesNotMatch(source, /hover:bg-app-selected/)
})
