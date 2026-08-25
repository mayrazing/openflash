import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = readFileSync(join(__dirname, 'useDeckInstalledPlugins.js'), 'utf8')
const cacheSrc = readFileSync(join(__dirname, 'deckInstalledPluginCache.js'), 'utf8')

test('hook fetches installed plugins by deckId', () => {
  assert.match(src, /loadDeckInstalledPlugins/)
  assert.match(src, /useEffect/)
})

test('hook skips fetch when deckId is falsy', () => {
  assert.match(src, /if \(!deckId\)/)
})

test('hook initializes installed plugins from shared deck cache', () => {
  assert.match(src, /getCachedDeckInstalledPlugins/)
  assert.match(src, /useState\(\(\)\s*=>\s*getCachedDeckInstalledPlugins\(deckId\)\s*\?\?\s*\[\]\)/)
})

test('shared deck installed plugin cache can store, read, and invalidate deck ids', () => {
  assert.match(cacheSrc, /setCachedDeckInstalledPlugins/)
  assert.match(cacheSrc, /getCachedDeckInstalledPlugins/)
  assert.match(cacheSrc, /invalidateDeckInstalledPlugins/)
})
