import { afterEach, beforeEach, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  activateAiProvider,
  createAiProvider,
  deleteAiProvider,
  discoverAiModels,
  getAiProviders,
  saveAiProvider,
} from './api.js'

const originalFetch = globalThis.fetch

beforeEach(() => {
  globalThis.fetch = async () => {
    throw new Error('fetch mock not configured')
  }
})

afterEach(() => {
  globalThis.fetch = originalFetch
})

test('getAiProviders uses provider list endpoint', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/providers')
    assert.equal(options.credentials, 'include')
    return response({ code: 200, data: [{ providerKey: 'deepseek' }] })
  }

  assert.deepEqual(await getAiProviders(), [{ providerKey: 'deepseek' }])
})

test('saveAiProvider sends PUT JSON body', async () => {
  const payload = { displayName: 'DeepSeek', baseUrl: 'https://api.deepseek.com/anthropic', model: 'deepseek-chat' }
  let capturedBody = null

  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/providers/deepseek')
    assert.equal(options.method, 'PUT')
    assert.equal(options.headers['Content-Type'], 'application/json')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: null })
  }

  await saveAiProvider('deepseek', payload)

  assert.deepEqual(capturedBody, payload)
})

test('createAiProvider posts JSON body without provider key', async () => {
  const payload = { displayName: 'DeepSeek', baseUrl: 'https://api.deepseek.com/anthropic', apiKey: 'sk', model: 'deepseek-chat' }
  let capturedBody = null

  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/providers')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Content-Type'], 'application/json')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: null })
  }

  await createAiProvider(payload)

  assert.deepEqual(capturedBody, payload)
})

test('activateAiProvider posts activation endpoint', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/providers/deepseek/activate?source=USER')
    assert.equal(options.method, 'POST')
    return response({ code: 200, data: null })
  }

  await activateAiProvider('deepseek')
})

test('deleteAiProvider deletes provider endpoint', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/providers/deepseek')
    assert.equal(options.method, 'DELETE')
    return response({ code: 200, data: null })
  }

  await deleteAiProvider('deepseek')
})

test('discoverAiModels posts temporary provider credentials', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/models/discover')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), {
      providerKey: 'deepseek', baseUrl: 'https://api.deepseek.com/anthropic', apiKey: null,
    })
    return response({ code: 200, data: [{ id: 'deepseek-v4-flash', name: 'deepseek-v4-flash' }] })
  }
  assert.equal((await discoverAiModels('deepseek', 'https://api.deepseek.com/anthropic', null))[0].id,
    'deepseek-v4-flash')
})

test('getPlatformModels reads a CLI catalog by offering key', async () => {
  const api = await import('./api.js')
  assert.equal(typeof api.getPlatformModels, 'function')
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/platform-offerings/platform-cli/models')
    assert.equal(options.method, undefined)
    assert.equal(options.credentials, 'include')
    return response({
      code: 200,
      data: { runtimeStatus: 'AVAILABLE', models: [{ model: 'gpt-5.4' }] },
    })
  }

  assert.deepEqual(await api.getPlatformModels('platform-cli'), {
    runtimeStatus: 'AVAILABLE',
    models: [{ model: 'gpt-5.4' }],
  })
})

test('ordinary-user AI API exports no login operations', async () => {
  const api = await import('./api.js')
  for (const forbidden of [
    'getPlatformLoginStatus',
    'startPlatformLogin',
    'cancelPlatformLogin',
  ]) {
    assert.equal(api[forbidden], undefined)
  }
})

test('savePlatformPreference sends CLI model and effort without activation', async () => {
  const api = await import('./api.js')
  assert.equal(typeof api.savePlatformPreference, 'function')
  const payload = { model: 'gpt-5.4', reasoningEffort: 'medium' }
  let capturedBody = null
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/platform-offerings/platform-cli/preference')
    assert.equal(options.method, 'PUT')
    assert.equal(options.credentials, 'include')
    assert.equal(options.headers['Content-Type'], 'application/json')
    capturedBody = JSON.parse(options.body)
    return response({ code: 200, data: null })
  }

  await api.savePlatformPreference('platform-cli', payload)

  assert.deepEqual(capturedBody, payload)
})

test('activatePlatformOffering uses its offering key instead of a personal provider endpoint', async () => {
  const api = await import('./api.js')
  assert.equal(typeof api.activatePlatformOffering, 'function')
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/settings/ai-config/platform-offerings/platform-cli/activate')
    assert.equal(options.method, 'POST')
    return response({ code: 200, data: null })
  }

  await api.activatePlatformOffering('platform-cli')
})

function response(payload, options = {}) {
  return {
    ok: options.ok ?? true,
    status: options.status ?? 200,
    async text() {
      return JSON.stringify(payload)
    },
  }
}
