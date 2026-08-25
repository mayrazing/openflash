import assert from 'node:assert/strict'
import test from 'node:test'
import {
  setDefaultDeckFromPopup,
  syncDefaultDeckAfterDeckLoad,
} from '../src/popupDefaultDeckActions.js'

const decks = [
  { id: 1, name: 'A' },
  { id: 2, name: 'B' },
]

function makeDeps() {
  const writes = []
  const messages = []
  return {
    writes,
    messages,
    setDefaultDeckId: async (deckId) => writes.push(deckId),
    refreshMenus: async () => messages.push({ type: 'OPENFLASH_REFRESH_MENUS' }),
  }
}

test('syncDefaultDeckAfterDeckLoad clears missing default deck', async () => {
  const deps = makeDeps()

  const result = await syncDefaultDeckAfterDeckLoad(decks, '9', deps)

  assert.deepEqual(result, { defaultDeckId: null, cleared: true })
  assert.deepEqual(deps.writes, [null])
})

test('syncDefaultDeckAfterDeckLoad keeps valid default deck', async () => {
  const deps = makeDeps()

  const result = await syncDefaultDeckAfterDeckLoad(decks, '2', deps)

  assert.deepEqual(result, { defaultDeckId: '2', cleared: false })
  assert.deepEqual(deps.writes, [])
})

test('setDefaultDeckFromPopup sets default deck and refreshes menus', async () => {
  const deps = makeDeps()

  const result = await setDefaultDeckFromPopup('1', '2', deps)

  assert.deepEqual(result, { defaultDeckId: '2', refreshError: null })
  assert.deepEqual(deps.writes, ['2'])
  assert.deepEqual(deps.messages, [{ type: 'OPENFLASH_REFRESH_MENUS' }])
})

test('setDefaultDeckFromPopup clears default deck and refreshes menus', async () => {
  const deps = makeDeps()

  const result = await setDefaultDeckFromPopup('2', '2', deps)

  assert.deepEqual(result, { defaultDeckId: null, refreshError: null })
  assert.deepEqual(deps.writes, [null])
  assert.deepEqual(deps.messages, [{ type: 'OPENFLASH_REFRESH_MENUS' }])
})

test('setDefaultDeckFromPopup rejects missing deck id without writing or refreshing', async () => {
  const deps = makeDeps()

  await assert.rejects(() => setDefaultDeckFromPopup('2', null, deps), {
    message: '缺少卡包',
  })
  assert.deepEqual(deps.writes, [])
  assert.deepEqual(deps.messages, [])
})

test('setDefaultDeckFromPopup returns refresh failures after saving', async () => {
  const deps = makeDeps()
  const refreshError = new Error('刷新失败')
  deps.refreshMenus = async () => {
    throw refreshError
  }

  const result = await setDefaultDeckFromPopup('1', '2', deps)

  assert.deepEqual(result, { defaultDeckId: '2', refreshError })
  assert.deepEqual(deps.writes, ['2'])
})
