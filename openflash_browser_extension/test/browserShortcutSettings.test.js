import assert from 'node:assert/strict'
import test from 'node:test'
import { openBrowserShortcutSettings, SHORTCUT_SETTINGS_URL } from '../src/browserShortcutSettings.js'

test('shortcut settings helper exposes browser shortcut settings url', () => {
  assert.equal(SHORTCUT_SETTINGS_URL, 'chrome://extensions/shortcuts')
})

test('openBrowserShortcutSettings opens browser shortcut settings page', async () => {
  const calls = []
  const tabs = {
    create(payload) {
      calls.push(payload)
      return Promise.resolve({ id: 7 })
    },
  }

  const result = await openBrowserShortcutSettings(tabs)

  assert.deepEqual(calls, [{ url: 'chrome://extensions/shortcuts' }])
  assert.deepEqual(result, { id: 7 })
})
