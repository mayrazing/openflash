import assert from 'node:assert/strict'
import test from 'node:test'
import * as contentScriptRuntime from '../src/content/contentScriptRuntime.js'

const { installContentScriptRuntime } = contentScriptRuntime

test('content script forwards notification messages to the page HUD', async () => {
  const listeners = []
  const shown = []
  const runtime = {
    onMessage: {
      addListener(listener) {
        listeners.push(listener)
      },
      removeListener(listener) {
        const index = listeners.indexOf(listener)
        if (index >= 0) listeners.splice(index, 1)
      },
    },
  }
  const cleanup = installContentScriptRuntime({
    runtime,
    pageNotification: {
      show(notification) {
        shown.push(notification)
      },
    },
    getSelectionHtml: () => ({ html: '', baseUrl: '' }),
  })

  listeners[0]({ type: 'OPENFLASH_SHOW_NOTIFICATION', message: '已保存', level: 'success' }, {}, () => {})

  assert.equal(shown.length, 1)
  assert.equal(shown[0].message, '已保存')
  assert.equal(shown[0].level, 'success')

  cleanup()
  assert.equal(listeners.length, 0)
})

test('content script refuses requests to open the privileged manual card editor', async () => {
  const listeners = []
  const opened = []
  installContentScriptRuntime({
    runtime: { onMessage: { addListener: (listener) => listeners.push(listener), removeListener() {} } },
    pageNotification: { show() {} },
    getSelectionHtml: () => ({ html: '', baseUrl: '' }),
  })
  const responses = []

  const keepChannelOpen = listeners[0]({
    type: 'OPENFLASH_OPEN_MANUAL_CARD',
    deckId: '7',
    baseUrl: 'http://localhost:5173',
    labels: { 'manualCard.title': '快速手动建卡' },
  }, {}, (response) => responses.push(response))
  assert.equal(keepChannelOpen, false)
  assert.deepEqual(opened, [])
  assert.deepEqual(responses, [])
})

test('content script returns false for unknown messages', () => {
  const listeners = []
  installContentScriptRuntime({
    runtime: { onMessage: { addListener: (listener) => listeners.push(listener), removeListener() {} } },
    pageNotification: { show() {} },
    getSelectionHtml: () => ({ html: '', baseUrl: '' }),
  })

  assert.equal(listeners[0]({ type: 'OPENFLASH_UNKNOWN' }, {}, () => {}), false)
})

test('install-once cleanup is idempotent and permits a fresh installation', () => {
  assert.equal(typeof contentScriptRuntime.installContentScriptOnce, 'function')
  const globalScope = {}
  const listeners = []
  let notificationFactories = 0
  let notificationDestroys = 0
  const deps = {
    globalScope,
    runtime: {
      onMessage: {
        addListener(listener) { listeners.push(listener) },
        removeListener(listener) {
          const index = listeners.indexOf(listener)
          if (index >= 0) listeners.splice(index, 1)
        },
      },
    },
    createPageNotification() {
      notificationFactories += 1
      return {
        show() {},
        destroy() { notificationDestroys += 1 },
      }
    },
    getSelectionHtml: () => ({ html: '', baseUrl: '' }),
  }

  const first = contentScriptRuntime.installContentScriptOnce(deps)
  const duplicate = contentScriptRuntime.installContentScriptOnce(deps)

  assert.equal(first, duplicate)
  assert.equal(notificationFactories, 1)
  assert.equal(listeners.length, 1)

  first.cleanup()
  first.cleanup()

  assert.equal(listeners.length, 0)
  assert.equal(notificationDestroys, 1)

  const reinstalled = contentScriptRuntime.installContentScriptOnce(deps)
  first.cleanup()
  const duplicateReinstall = contentScriptRuntime.installContentScriptOnce(deps)

  assert.notEqual(reinstalled, first)
  assert.equal(duplicateReinstall, reinstalled)
  assert.equal(notificationFactories, 2)
  assert.equal(listeners.length, 1)

  reinstalled.cleanup()
  assert.equal(listeners.length, 0)
  assert.equal(notificationDestroys, 2)
})

test('content script extracts current selection through selection adapter', () => {
  const listeners = []
  installContentScriptRuntime({
    runtime: { onMessage: { addListener: (listener) => listeners.push(listener), removeListener() {} } },
    pageNotification: { show() {} },
    getSelectionHtml: () => ({
      html: '<p>Hello <img src="/cover.png"></p>',
      baseUrl: 'https://example.com/notes/page.html',
    }),
  })
  const responses = []

  const keepChannelOpen = listeners[0]({ type: 'OPENFLASH_EXTRACT_SELECTION' }, {}, (response) => responses.push(response))

  assert.equal(keepChannelOpen, false)
  assert.deepEqual(responses, [{
    ok: true,
    selection: { sideA: 'Hello', imageSources: ['https://example.com/cover.png'] },
  }])
})
