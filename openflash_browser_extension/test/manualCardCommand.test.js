import assert from 'node:assert/strict'
import test from 'node:test'
import { createManualCardCommandHandler, MANUAL_CARD_COMMAND } from '../src/manualCardCommand.js'

function deps(overrides = {}) {
  return {
    getServiceUrl: async () => 'http://localhost:5173',
    getDefaultDeckId: async () => '7',
    setDefaultDeckId: async () => {},
    ensureBrowserImportEnabled: async () => {},
    listDecks: async () => [{ id: 7, name: 'Default' }],
    readSelectedText: async () => '',
    openEditor: async () => {},
    tabs: { sendMessage: async () => ({ ok: true }) },
    scripting: { executeScript: async () => [{ result: true }] },
    notify: async () => {},
    t: (key) => key,
    ...overrides,
  }
}

test('manual card command ignores non-matching commands', async () => {
  const handler = createManualCardCommandHandler(deps())

  assert.equal(await handler('unknown-command', { id: 3 }), false)
})

test('manual card command requires default deck', async () => {
  const messages = []
  const handler = createManualCardCommandHandler(deps({
    getDefaultDeckId: async () => null,
    notify: async (message, level, tabId) => messages.push({ message, level, tabId }),
  }))

  assert.equal(await handler(MANUAL_CARD_COMMAND, { id: 3 }), true)
  assert.deepEqual(messages, [{ message: 'menu.defaultDeckRequired', level: 'error', tabId: 3 }])
})

test('manual card command clears deleted default deck before opening dialog', async () => {
  const cleared = []
  const messages = []
  const handler = createManualCardCommandHandler(deps({
    listDecks: async () => [{ id: 8, name: 'Other' }],
    setDefaultDeckId: async (value) => cleared.push(value),
    notify: async (message, level, tabId) => messages.push({ message, level, tabId }),
  }))

  assert.equal(await handler(MANUAL_CARD_COMMAND, { id: 3 }), true)
  assert.deepEqual(cleared, [null])
  assert.deepEqual(messages, [{ message: 'menu.defaultDeckUnavailable', level: 'error', tabId: 3 }])
})

test('manual card command opens an extension-owned editor with current selection', async () => {
  const calls = []
  const handler = createManualCardCommandHandler(deps({
    readSelectedText: async (tabId) => {
      assert.equal(tabId, 3)
      return 'selected text'
    },
    openEditor: async (context) => calls.push(context),
  }))

  assert.equal(await handler(MANUAL_CARD_COMMAND, { id: 3 }), true)
  assert.deepEqual(calls, [{
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    sourceTabId: 3,
    labels: {
      'manualCard.cancel': 'manualCard.cancel',
      'manualCard.emptyContent': 'manualCard.emptyContent',
      'manualCard.imageProcessFailed': 'manualCard.imageProcessFailed',
      'manualCard.imageTooLarge': 'manualCard.imageTooLarge',
      'manualCard.imagesTooLarge': 'manualCard.imagesTooLarge',
      'manualCard.save': 'manualCard.save',
      'manualCard.saveFailed': 'manualCard.saveFailed',
      'manualCard.saved': 'manualCard.saved',
      'manualCard.saving': 'manualCard.saving',
      'manualCard.sideA': 'manualCard.sideA',
      'manualCard.sideB': 'manualCard.sideB',
      'manualCard.title': 'manualCard.title',
      'manualCard.tooManyImages': 'manualCard.tooManyImages',
      'manualCard.unsavedBack': 'manualCard.unsavedBack',
      'manualCard.unsavedConfirm': 'manualCard.unsavedConfirm',
      'manualCard.unsavedTitle': 'manualCard.unsavedTitle',
    },
    selectedText: 'selected text',
  }])
})

test('manual card command does not inject the editor into the active page', async () => {
  const injected = []
  const opened = []
  const handler = createManualCardCommandHandler(deps({
    tabs: {
      sendMessage: async () => { throw new Error('must not message page') },
    },
    scripting: {
      executeScript: async (options) => {
        injected.push(options)
        return [{ result: true }]
      },
    },
    readSelectedText: async () => '',
    openEditor: async (context) => opened.push(context),
  }))

  await handler(MANUAL_CARD_COMMAND, { id: 3 })

  assert.deepEqual(injected, [])
  assert.equal(opened.length, 1)
})

test('manual card command still opens a blank editor when page selection is inaccessible', async () => {
  const messages = []
  const opened = []
  const handler = createManualCardCommandHandler(deps({
    readSelectedText: async () => { throw new Error('Cannot access a chrome:// URL') },
    openEditor: async (context) => opened.push(context),
    notify: async (message, level, tabId) => messages.push({ message, level, tabId }),
  }))

  await handler(MANUAL_CARD_COMMAND, { id: 3 })

  assert.deepEqual(messages, [])
  assert.equal(opened[0].selectedText, '')
})
