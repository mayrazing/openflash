import assert from 'node:assert/strict'
import test from 'node:test'
import { getDefaultDeckId, setDefaultDeckId } from '../src/storage.js'

function installChromeStorage(initial = {}) {
  const store = { ...initial }
  globalThis.chrome = {
    storage: {
      local: {
        async get(key) {
          return { [key]: store[key] }
        },
        async set(values) {
          Object.assign(store, values)
        },
      },
    },
  }
  return store
}

test('default deck id is read from chrome local storage', async () => {
  installChromeStorage({ defaultDeckId: '42' })
  assert.equal(await getDefaultDeckId(), '42')
})

test('default deck id falls back to null when missing', async () => {
  installChromeStorage()
  assert.equal(await getDefaultDeckId(), null)
})

test('setDefaultDeckId stores deck id as string', async () => {
  const store = installChromeStorage()
  await setDefaultDeckId(42)
  assert.equal(store.defaultDeckId, '42')
})

test('setDefaultDeckId clears deck id with null', async () => {
  const store = installChromeStorage({ defaultDeckId: '42' })
  await setDefaultDeckId(null)
  assert.equal(store.defaultDeckId, null)
})
