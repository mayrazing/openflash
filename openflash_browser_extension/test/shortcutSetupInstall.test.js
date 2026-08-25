import assert from 'node:assert/strict'
import test from 'node:test'
import { openShortcutSetupOnInstall } from '../src/shortcutSetupInstall.js'

test('openShortcutSetupOnInstall opens setup page on first install', () => {
  const calls = []
  const runtime = {
    getURL(path) {
      return `chrome-extension://openflash/${path}`
    },
  }
  const tabs = {
    create(payload) {
      calls.push(payload)
    },
  }

  openShortcutSetupOnInstall({ reason: 'install' }, { runtime, tabs })

  assert.deepEqual(calls, [{ url: 'chrome-extension://openflash/shortcutSetup.html' }])
})

test('openShortcutSetupOnInstall does not open setup page on update', () => {
  const calls = []
  const runtime = {
    getURL(path) {
      return `chrome-extension://openflash/${path}`
    },
  }
  const tabs = {
    create(payload) {
      calls.push(payload)
    },
  }

  openShortcutSetupOnInstall({ reason: 'update' }, { runtime, tabs })

  assert.deepEqual(calls, [])
})
