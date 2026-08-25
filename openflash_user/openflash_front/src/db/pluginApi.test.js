import test, { afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { getPluginCatalog, getInstalledPlugins, savePluginInstall } from './database.js'
import { getCachedDeckInstalledPlugins, setCachedDeckInstalledPlugins } from '../plugins/deckInstalledPluginCache.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

function mockJson(payload) {
  globalThis.fetch = async () => ({ ok: true, status: 200, text: async () => JSON.stringify(payload) })
}

test('getPluginCatalog returns data array', async () => {
  mockJson({ code: 200, data: [{ pluginId: 'tts', name: 'TTS 英语', config: '{}' }] })
  const list = await getPluginCatalog()
  assert.equal(list[0].pluginId, 'tts')
})

test('getInstalledPlugins passes deckId and returns ids', async () => {
  let calledUrl = ''
  globalThis.fetch = async (url) => { calledUrl = url; return { ok: true, status: 200, text: async () => JSON.stringify({ code: 200, data: ['tts'] }) } }
  const ids = await getInstalledPlugins(9)
  assert.match(calledUrl, /deckId=9/)
  assert.deepEqual(ids, ['tts'])
})

test('savePluginInstall posts pluginId and deck groups', async () => {
  let body = null
  globalThis.fetch = async (url, opt) => { body = JSON.parse(opt.body); return { ok: true, status: 200, text: async () => JSON.stringify({ code: 200, data: null }) } }
  await savePluginInstall('tts', [9, 10], [11])
  assert.equal(body.pluginId, 'tts')
  assert.deepEqual(body.installDeckIds, [9, 10])
  assert.deepEqual(body.uninstallDeckIds, [11])
})

test('savePluginInstall invalidates affected deck installed plugin cache', async () => {
  setCachedDeckInstalledPlugins(9, ['tts'])
  setCachedDeckInstalledPlugins(11, ['tts', 'mask-mode'])
  globalThis.fetch = async () => ({ ok: true, status: 200, text: async () => JSON.stringify({ code: 200, data: null }) })

  await savePluginInstall('mask-mode', [9], [11])

  assert.equal(getCachedDeckInstalledPlugins(9), undefined)
  assert.equal(getCachedDeckInstalledPlugins(11), undefined)
})
