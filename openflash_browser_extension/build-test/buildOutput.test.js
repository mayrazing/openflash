import assert from 'node:assert/strict'
import { access, readFile } from 'node:fs/promises'
import test from 'node:test'

const root = new URL('../dist/', import.meta.url)

test('dist contains loadable Manifest V3 entry points', async () => {
  const manifest = JSON.parse(await readFile(new URL('manifest.json', root), 'utf8'))
  assert.equal(manifest.manifest_version, 3)
  assert.equal(manifest.background.service_worker, 'assets/background.js')
  assert.equal(manifest.background.type, 'module')
  assert.deepEqual(manifest.content_scripts[0].js, ['assets/contentScript.js'])
  assert.equal(manifest.action.default_popup, 'popup.html')
  await Promise.all([
    'popup.html',
    'manualCard.html',
    'shortcutSetup.html',
    manifest.background.service_worker,
    manifest.content_scripts[0].js[0],
    ...Object.values(manifest.icons),
    ...Object.values(manifest.action.default_icon),
  ].map((path) => access(new URL(path, root))))
})

test('dist does not reference source modules', async () => {
  const manifestText = await readFile(new URL('manifest.json', root), 'utf8')
  assert.doesNotMatch(manifestText, /"src\//)
})

test('extension pages do not emit modulepreload links rejected by Chrome', async () => {
  const html = await Promise.all([
    'popup.html',
    'manualCard.html',
    'shortcutSetup.html',
  ].map((path) => readFile(new URL(path, root), 'utf8')))

  for (const page of html) {
    assert.doesNotMatch(page, /<link\b[^>]*\brel=["']modulepreload["']/i)
  }
})

test('content script does not depend on Node process globals', async () => {
  const contentScript = await readFile(new URL('assets/contentScript.js', root), 'utf8')
  assert.doesNotMatch(contentScript, /\bprocess\.env\.NODE_ENV\b/)
  assert.doesNotMatch(contentScript, /OPENFLASH_OPEN_MANUAL_CARD|OPENFLASH_MANUAL_CARD_CREATE/)
})
