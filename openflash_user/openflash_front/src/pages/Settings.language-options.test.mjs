import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./Settings.jsx', import.meta.url), 'utf8')

test('Settings disables language buttons until registry options are loaded', () => {
  assert.match(source, /const \[languageOptionsLoaded, setLanguageOptionsLoaded\] = useState\(false\)/)
  assert.match(source, /const languageControlsDisabled = !settingsLoaded \|\| !languageOptionsLoaded/)
  assert.match(source, /disabled=\{languageControlsDisabled\}/)
})

test('Settings ignores language clicks before registry options are loaded', () => {
  assert.match(source, /if \(!settingsLoaded \|\| !languageOptionsLoaded\) return/)
})
