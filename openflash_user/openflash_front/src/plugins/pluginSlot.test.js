import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

function read(relativePath) {
  return readFileSync(join(__dirname, relativePath), 'utf8')
}

test('PluginSlot sorts slot items by order', () => {
  const source = read('pluginSlot.jsx')
  assert.match(source, /sort\(\(left,\s*right\)/)
  assert.match(source, /left\.order\s*-\s*right\.order/)
})

test('PluginSlot accepts object slot entries with component and order', () => {
  const source = read('pluginSlot.jsx')
  assert.match(source, /entry\.component/)
  assert.match(source, /entry\.order/)
})

test('PluginSlot wraps plugin blocks for layout isolation', () => {
  const source = read('pluginSlot.jsx')
  assert.match(source, /plugin-slot-block/)
})

test('PluginSlot uses error boundary per plugin block', () => {
  const source = read('pluginSlot.jsx')
  assert.match(source, /PluginSlotErrorBoundary/)
})

test('usePluginActionSlot exposes non-visual plugin actions', () => {
  const source = read('usePluginActionSlot.js')
  assert.match(source, /entry\.action/)
  assert.match(source, /allowedIds\.includes/)
  assert.match(source, /sort\(\(left,\s*right\)/)
})

test('plugin action slot exposes active plugin loading state', () => {
  const pluginState = read('pluginState.js')
  const provider = read('PluginContext.jsx')
  const actionSlot = read('usePluginActionSlot.js')

  assert.match(pluginState, /activeIds:\s*\[\],\s*loaded:\s*false/)
  assert.match(pluginState, /export function usePluginState/)
  assert.match(pluginState, /export function useActivePluginsLoaded/)
  assert.match(provider, /\.finally\(\(\)\s*=>\s*setLoaded\(true\)\)/)
  assert.match(actionSlot, /export function usePluginActionSlotState/)
  assert.match(actionSlot, /return useMemo\(\(\)\s*=>\s*\(\{ loaded:\s*effectiveLoaded,\s*actions \}\)/)
})

test('plugin action slot isolates failing plugin action factories', () => {
  const source = read('usePluginActionSlot.js')

  assert.match(source, /try\s*\{/)
  assert.match(source, /catch\s*\(/)
  assert.match(source, /appWarn/)
  assert.match(source, /return null/)
})
