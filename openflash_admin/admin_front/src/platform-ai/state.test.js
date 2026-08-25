import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const state = await import('./state.js').catch(() => ({}))
const stateSource = await readFile(new URL('./state.js', import.meta.url), 'utf8').catch(() => '')

test('platform state fails closed while preserving safe offline catalog metadata', () => {
  assert.equal(typeof state.normalizePlatformAiPage, 'function')
  assert.deepEqual(state.normalizePlatformAiPage({
    runtimeStatus: 'ERROR',
    runtimeAvailable: false,
    connections: [{
      connectionKey: 'platform-api-one',
      source: 'PLATFORM',
      kind: 'API',
      protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com',
      credentialsConfigured: true,
      enabled: true,
      sortOrder: 1,
      offerings: [{
        offeringKey: 'platform-model-one',
        source: 'PLATFORM',
        modelKey: 'claude-3-5-sonnet',
        enabled: true,
        defaultAccess: false,
        sortOrder: 2,
        runtimeStatus: 'ERROR',
      }],
    }],
  }), {
    runtimeStatus: 'ERROR',
    runtimeAvailable: false,
    connections: [{
      connectionKey: 'platform-api-one',
      source: 'PLATFORM',
      kind: 'API',
      protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com',
      credentialsConfigured: true,
      enabled: true,
      sortOrder: 1,
      offerings: [{
        offeringKey: 'platform-model-one',
        source: 'PLATFORM',
        modelKey: 'claude-3-5-sonnet',
        enabled: true,
        defaultAccess: false,
        sortOrder: 2,
        runtimeStatus: 'ERROR',
      }],
    }],
  })
})

test('platform state uses source alone to accept platform catalog items', () => {
  assert.deepEqual(state.normalizePlatformAiPage({
    runtimeStatus: 'AVAILABLE',
    runtimeAvailable: true,
    connections: [
      {
        connectionKey: 'provider-shaped-user-item', source: 'USER', kind: 'CLI',
        protocol: 'CODEX_APP_SERVER', offerings: [],
      },
      {
        connectionKey: 'platform-item', source: 'PLATFORM', kind: 'API',
        protocol: 'ANTHROPIC', offerings: [
          { offeringKey: 'user-offering', source: 'USER' },
          { offeringKey: 'platform-offering', source: 'PLATFORM' },
        ],
      },
    ],
  }).connections, [{
    connectionKey: 'platform-item', source: 'PLATFORM', kind: 'API',
    protocol: 'ANTHROPIC', offerings: [
      { offeringKey: 'platform-offering', source: 'PLATFORM' },
    ],
  }])
})

test('Codex creation is allowed only while runtime is online and its fixed CLI connection is absent', () => {
  assert.equal(typeof state.canCreateCodexConnection, 'function')
  const api = {
    runtimeAvailable: true,
    connections: [{ source: 'PLATFORM', kind: 'API', protocol: 'ANTHROPIC' }],
  }
  assert.equal(state.canCreateCodexConnection(api), true)
  assert.equal(state.canCreateCodexConnection({
    ...api,
    connections: [
      ...api.connections,
      { source: 'PLATFORM', kind: 'CLI', protocol: 'CODEX_APP_SERVER' },
    ],
  }), false)
  assert.equal(state.canCreateCodexConnection({ ...api, runtimeAvailable: false }), false)
  assert.equal(state.canCreateCodexConnection(api), true)
})

test('connection creation payloads cannot contain arbitrary CLI keys', () => {
  assert.deepEqual(state.connectionCreatePayload('API', 'https://api.anthropic.com', 3), {
    kind: 'API', protocol: 'ANTHROPIC', cliKey: null,
    displayName: null, baseUrl: 'https://api.anthropic.com', sortOrder: 3,
  })
  assert.deepEqual(state.connectionCreatePayload('CODEX', '', 4), {
    kind: 'CLI', protocol: 'CODEX_APP_SERVER', cliKey: 'codex', displayName: null,
    baseUrl: null, sortOrder: 4,
  })
  assert.throws(() => state.connectionCreatePayload('future-cli', '', 0))
})

test('platform state contains no dead optimistic offering path', () => {
  assert.doesNotMatch(stateSource, /optimisticOffering/)
})
