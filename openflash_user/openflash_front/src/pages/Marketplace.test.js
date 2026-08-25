import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = readFileSync(join(__dirname, 'Marketplace.jsx'), 'utf8')

test('marketplace has all / installed tabs', () => {
  assert.match(src, /marketplace\.tabAll/)
  assert.match(src, /marketplace\.tabInstalled/)
})

test('marketplace loads catalog and opens install dialog', () => {
  assert.match(src, /getPluginCatalog/)
  assert.match(src, /PluginInstallDialog/)
  assert.match(src, /savePluginInstall/)
})

test('marketplace plugin action stays inline so card text keeps its width', () => {
  assert.match(src, /<Button inline small rounded tonal=/)
})

test('marketplace shows disabled notice when marketplace flag is off (50301)', () => {
  assert.match(src, /50301/)
  assert.match(src, /marketplace\.disabled/)
})

test('marketplace query targets and opens the matching plugin dialog', () => {
  assert.match(src, /useSearchParams/)
  assert.match(src, /searchParams\.get\('plugin'\)/)
  assert.match(src, /catalog\.find\(plugin => plugin\.pluginId === targetPluginId\)/)
  assert.match(src, /setDialogPlugin\(targetPlugin\)/)
})

test('marketplace loads installed deck ids before targeted dialog can open', () => {
  const installMapReady = src.indexOf('setInstallMap(map)')
  const catalogVisible = src.indexOf('setCatalog(cat)')

  assert.notEqual(installMapReady, -1)
  assert.notEqual(catalogVisible, -1)
  assert.ok(installMapReady < catalogVisible)
})
