import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'

const platformApi = await import('./api.js').catch(() => ({}))

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function successResponse(data = null) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ code: 200, data }),
  }
}

function captureFetch(data = null) {
  const calls = []
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    return successResponse(data)
  }
  return calls
}

test('platform AI API exposes the complete management contract', () => {
  for (const name of [
    'createPlatformConnection',
    'createPlatformOffering',
    'deletePlatformConnection',
    'deletePlatformOffering',
    'deletePlatformOfferingUserAccess',
    'discoverPlatformModels',
    'discoverPlatformModelsForConfiguration',
    'getPlatformAiPage',
    'replacePlatformCredentials',
    'setPlatformOfferingDefaultAccess',
    'setPlatformOfferingUserAccess',
    'updatePlatformConnection',
    'updatePlatformOffering',
  ]) {
    assert.equal(typeof platformApi[name], 'function', `missing ${name}`)
  }
})

test('connection API sends fixed create, update, credential and delete payloads', async () => {
  const calls = captureFetch({ runtimeAvailable: true, connections: [] })

  await platformApi.getPlatformAiPage()
  await platformApi.createPlatformConnection({
    kind: 'API', protocol: 'ANTHROPIC', cliKey: null,
    baseUrl: 'https://api.anthropic.com', sortOrder: 4,
  })
  await platformApi.updatePlatformConnection('connection/one', {
    baseUrl: 'https://api.anthropic.com', enabled: false, sortOrder: 5,
  })
  await platformApi.replacePlatformCredentials('connection/one', 'new-secret')
  await platformApi.deletePlatformConnection('connection/one')

  assert.deepEqual(calls.map(call => [call.path, call.options.method, call.options.body]), [
    ['/api/admin/platform-ai', undefined, undefined],
    ['/api/admin/platform-ai/connections', 'POST', JSON.stringify({
      kind: 'API', protocol: 'ANTHROPIC', cliKey: null,
      baseUrl: 'https://api.anthropic.com', sortOrder: 4,
    })],
    ['/api/admin/platform-ai/connections/connection%2Fone', 'PUT', JSON.stringify({
      baseUrl: 'https://api.anthropic.com', enabled: false, sortOrder: 5,
    })],
    ['/api/admin/platform-ai/connections/connection%2Fone/credentials', 'PUT', JSON.stringify({
      apiKey: 'new-secret',
    })],
    ['/api/admin/platform-ai/connections/connection%2Fone', 'DELETE', undefined],
  ])
  assert.equal(calls[0].options.credentials, 'include')
})

test('offering and access API encodes keys and sends only accepted fields', async () => {
  const calls = captureFetch([{ modelKey: 'claude-discovered' }])

  const models = await platformApi.discoverPlatformModels('connection/one')
  await platformApi.createPlatformOffering('connection/one', 'claude-manual', 2)
  await platformApi.updatePlatformOffering('offering/one', {
    modelKey: 'claude-updated', enabled: false, sortOrder: 3,
  })
  await platformApi.setPlatformOfferingDefaultAccess('offering/one', true)
  await platformApi.setPlatformOfferingUserAccess('offering/one', 'user/8', false)
  await platformApi.deletePlatformOfferingUserAccess('offering/one', 'user/8')
  await platformApi.deletePlatformOffering('offering/one')

  assert.deepEqual(models, [{ modelKey: 'claude-discovered' }])
  assert.deepEqual(calls.map(call => [call.path, call.options.method, call.options.body]), [
    ['/api/admin/platform-ai/connections/connection%2Fone/models/discover', 'POST', undefined],
    ['/api/admin/platform-ai/connections/connection%2Fone/offerings', 'POST', JSON.stringify({
      modelKey: 'claude-manual', sortOrder: 2,
    })],
    ['/api/admin/platform-ai/offerings/offering%2Fone', 'PUT', JSON.stringify({
      modelKey: 'claude-updated', enabled: false, sortOrder: 3,
    })],
    ['/api/admin/platform-ai/offerings/offering%2Fone/access/default', 'PUT', JSON.stringify({ enabled: true })],
    ['/api/admin/platform-ai/offerings/offering%2Fone/access/users/user%2F8', 'PUT', JSON.stringify({ enabled: false })],
    ['/api/admin/platform-ai/offerings/offering%2Fone/access/users/user%2F8', 'DELETE', undefined],
    ['/api/admin/platform-ai/offerings/offering%2Fone', 'DELETE', undefined],
  ])
})

test('draft model discovery sends credentials without persisting a connection', async () => {
  const calls = captureFetch([{ modelKey: 'kimi-k2' }])

  const models = await platformApi.discoverPlatformModelsForConfiguration(
    'https://api.moonshot.cn/anthropic', 'draft-secret')

  assert.deepEqual(models, [{ modelKey: 'kimi-k2' }])
  assert.deepEqual(calls.map(call => [call.path, call.options.method, call.options.body]), [[
    '/api/admin/platform-ai/models/discover',
    'POST',
    JSON.stringify({ baseUrl: 'https://api.moonshot.cn/anthropic', apiKey: 'draft-secret' }),
  ]])
})
