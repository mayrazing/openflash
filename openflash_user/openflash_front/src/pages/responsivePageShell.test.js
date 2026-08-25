import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const files = {
  Home: readFileSync(new URL('./Home.jsx', import.meta.url), 'utf8'),
  DeckDetail: readFileSync(new URL('./DeckDetail.jsx', import.meta.url), 'utf8'),
  Settings: readFileSync(new URL('./Settings.jsx', import.meta.url), 'utf8'),
  Marketplace: readFileSync(new URL('./Marketplace.jsx', import.meta.url), 'utf8'),
  Mastered: readFileSync(new URL('./Mastered.jsx', import.meta.url), 'utf8'),
  Summary: readFileSync(new URL('./Summary.jsx', import.meta.url), 'utf8'),
  Auth: readFileSync(new URL('./Auth.jsx', import.meta.url), 'utf8'),
  DeckSettings: readFileSync(new URL('./DeckSettings.jsx', import.meta.url), 'utf8'),
}

test('core pages use AppPage instead of raw max-w page wrapper', () => {
  for (const [name, source] of Object.entries(files)) {
    assert.match(source, /AppPage/, `${name} should import and render AppPage`)
  }
})

test('selection bottom bars use BottomActionBar safe-area shell', () => {
  const home = files.Home
  const selectActionBar = readFileSync(new URL('../components/SelectActionBar.jsx', import.meta.url), 'utf8')
  assert.match(home, /BottomActionBar/)
  assert.match(selectActionBar, /BottomActionBar/)
  assert.doesNotMatch(selectActionBar, /fixed bottom-0 left-0 right-0 z-50/)
})

test('deck settings navbar gives long titles the remaining row width', () => {
  const deckSettings = files.DeckSettings

  assert.match(deckSettings, /centerTitle=\{false\}/)
  assert.match(deckSettings, /titleClassName="min-w-0 flex-1 overflow-hidden"/)
  assert.match(deckSettings, /<h1 className="truncate">/)
})
