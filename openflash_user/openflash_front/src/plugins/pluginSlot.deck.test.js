import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const slot = readFileSync(join(__dirname, 'pluginSlot.jsx'), 'utf8')
const actionSlot = readFileSync(join(__dirname, 'usePluginActionSlot.js'), 'utf8')

test('PluginSlot accepts deckId and filters by per-deck installed ids', () => {
  assert.match(slot, /deckId/)
  assert.match(slot, /useDeckInstalledPlugins/)
})

test('PluginSlot falls back to global activeIds when no deckId', () => {
  assert.match(slot, /deckId\s*\?\s*installedIds\s*:\s*globalActiveIds/)
})

test('usePluginActionSlotState accepts optional deckId third parameter', () => {
  assert.match(actionSlot, /usePluginActionSlotState\(slotName/)
  assert.match(actionSlot, /deckId/)
  assert.match(actionSlot, /useDeckInstalledPlugins/)
})

test('usePluginActionSlotState falls back to global activeIds when no deckId', () => {
  assert.match(actionSlot, /deckId\s*\?\s*installedIds\s*:\s*globalActiveIds/)
})
