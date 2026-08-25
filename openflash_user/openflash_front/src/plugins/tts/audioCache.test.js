import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createTtsAudioCache } from './audioCache.js'

test('IndexedDB TTS cache stores WAV by normalized text', async () => {
  const cache = createTtsAudioCache(createFakeIndexedDb())
  const audio = new Blob(['wav'], { type: 'audio/wav' })

  assert.equal(await cache.get('hello'), null)
  assert.equal(await cache.put('hello', audio), true)
  assert.equal(await cache.get('hello'), audio)
  assert.equal(await cache.get('hello.'), null)
  assert.equal(await cache.remove('hello'), true)
  assert.equal(await cache.get('hello'), null)
})

test('TTS cache degrades cleanly when IndexedDB is unavailable', async () => {
  const cache = createTtsAudioCache(undefined)

  assert.equal(await cache.get('hello'), null)
  assert.equal(await cache.put('hello', new Blob(['wav'])), false)
  assert.equal(await cache.remove('hello'), false)
})

function createFakeIndexedDb() {
  const values = new Map()
  let storeCreated = false
  const database = {
    objectStoreNames: {
      contains() {
        return storeCreated
      },
    },
    createObjectStore() {
      storeCreated = true
    },
    close() {},
    transaction() {
      const transaction = {
        objectStore() {
          return {
            get(key) {
              const request = {}
              queueMicrotask(() => {
                request.result = values.get(key)
                request.onsuccess?.()
              })
              return request
            },
            put(value, key) {
              queueMicrotask(() => {
                values.set(key, value)
                transaction.oncomplete?.()
              })
            },
            delete(key) {
              queueMicrotask(() => {
                values.delete(key)
                transaction.oncomplete?.()
              })
            },
          }
        },
      }
      return transaction
    },
  }

  return {
    open() {
      const request = {}
      queueMicrotask(() => {
        request.result = database
        request.onupgradeneeded?.()
        request.onsuccess?.()
      })
      return request
    },
  }
}
