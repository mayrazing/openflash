import assert from 'node:assert/strict'
import test from 'node:test'
import {
  consumeManualCardWindowContext,
  createManualCardWindowManager,
  isTrustedManualCardSender,
} from '../src/manualCardWindow.js'

function fakeStorageSession() {
  const values = {}
  return {
    values,
    async get(key) {
      if (typeof key === 'string') return { [key]: values[key] }
      return { ...values }
    },
    async set(entries) { Object.assign(values, entries) },
    async remove(key) { delete values[key] },
  }
}

test('manual card window stores context outside the URL and opens an extension popup', async () => {
  const storageSession = fakeStorageSession()
  const created = []
  const manager = createManualCardWindowManager({
    storageSession,
    windows: {
      create: async (options) => {
        created.push(options)
        return { id: 41 }
      },
      update: async () => { throw new Error('not expected') },
    },
    runtime: { getURL: (path) => `chrome-extension://openflash/${path}` },
    randomUUID: () => 'context-token',
  })
  const context = {
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    sourceTabId: 3,
    labels: { 'manualCard.title': 'Manual card' },
    selectedText: 'selected text',
  }

  await manager.open(context)

  assert.deepEqual(created, [{
    focused: true,
    height: 450,
    type: 'popup',
    url: 'chrome-extension://openflash/manualCard.html?context=context-token',
    width: 480,
  }])
  assert.deepEqual(storageSession.values['manualCardContext:context-token'], context)
  assert.equal(storageSession.values.manualCardWindowId, 41)
  assert.doesNotMatch(created[0].url, /localhost|deckId|selected text/)
})

test('manual card command focuses an existing editor without replacing its draft context', async () => {
  const storageSession = fakeStorageSession()
  storageSession.values.manualCardWindowId = 41
  const updates = []
  let creates = 0
  const manager = createManualCardWindowManager({
    storageSession,
    windows: {
      create: async () => { creates += 1; return { id: 42 } },
      update: async (windowId, options) => updates.push({ windowId, options }),
    },
    runtime: { getURL: (path) => `chrome-extension://openflash/${path}` },
    randomUUID: () => 'unused-token',
  })

  const result = await manager.open({ baseUrl: 'http://localhost:5173', deckId: '8' })

  assert.deepEqual(result, { reused: true, windowId: 41 })
  assert.deepEqual(updates, [{ windowId: 41, options: { focused: true } }])
  assert.equal(creates, 0)
  assert.equal(storageSession.values['manualCardContext:unused-token'], undefined)
})

test('manual card page consumes its one-time context', async () => {
  const storageSession = fakeStorageSession()
  storageSession.values['manualCardContext:abc'] = {
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    sourceTabId: 3,
    labels: { 'manualCard.title': 'Manual card' },
    selectedText: 'selected text',
  }

  const first = await consumeManualCardWindowContext({
    search: '?context=abc',
    storageSession,
  })
  const second = await consumeManualCardWindowContext({
    search: '?context=abc',
    storageSession,
  })

  assert.deepEqual(first, {
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    sourceTabId: 3,
    labels: { 'manualCard.title': 'Manual card' },
    selectedText: 'selected text',
  })
  assert.equal(second, null)
})

test('manual card page rejects a malformed source tab id', async () => {
  const storageSession = fakeStorageSession()
  storageSession.values['manualCardContext:abc'] = {
    baseUrl: 'http://localhost:5173',
    deckId: '7',
    sourceTabId: '3',
  }

  const context = await consumeManualCardWindowContext({
    search: '?context=abc',
    storageSession,
  })

  assert.equal(context, null)
})

test('manual card save sender must match extension scheme, id and page exactly', () => {
  const pageUrl = 'chrome-extension://openflash/manualCard.html'

  assert.equal(isTrustedManualCardSender({ url: `${pageUrl}?context=abc` }, pageUrl), true)
  assert.equal(isTrustedManualCardSender({ url: 'chrome-extension://evil/manualCard.html' }, pageUrl), false)
  assert.equal(isTrustedManualCardSender({ url: 'chrome-extension://openflash/popup.html' }, pageUrl), false)
  assert.equal(isTrustedManualCardSender({ url: 'https://openflash/manualCard.html' }, pageUrl), false)
})
