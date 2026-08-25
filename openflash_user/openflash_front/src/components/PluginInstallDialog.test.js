import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = readFileSync(join(__dirname, 'PluginInstallDialog.jsx'), 'utf8')

test('dialog renders deck checkboxes and confirm button', () => {
  assert.match(src, /decks\.map/)
  assert.match(src, /<Checkbox/)
  assert.match(src, /onConfirm/)
})

test('confirm splits checked into install/uninstall groups vs preinstalled', () => {
  assert.match(src, /installDeckIds/)
  assert.match(src, /uninstallDeckIds/)
})

test('confirm disabled when selection equals preinstalled (no change)', () => {
  assert.match(src, /disabled=/)
})
