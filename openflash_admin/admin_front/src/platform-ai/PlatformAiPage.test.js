import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createInstance } from 'i18next'
import { I18nextProvider, initReactI18next } from 'react-i18next'
import { createServer } from 'vite'
import en from '../locales/en.json' with { type: 'json' }

const pageSource = await readFile(new URL('./PlatformAiPage.jsx', import.meta.url), 'utf8').catch(() => '')
const connectionDialogSource = await readFile(new URL('./PlatformConnectionDialog.jsx', import.meta.url), 'utf8').catch(() => '')
const offeringDialogSource = await readFile(new URL('./PlatformOfferingDialog.jsx', import.meta.url), 'utf8').catch(() => '')

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://admin.test/platform-ai' })
Object.assign(globalThis, {
  document: dom.window.document,
  Event: dom.window.Event,
  HTMLElement: dom.window.HTMLElement,
  IS_REACT_ACT_ENVIRONMENT: true,
  MouseEvent: dom.window.MouseEvent,
  Node: dom.window.Node,
  window: dom.window,
})
Object.defineProperty(globalThis, 'navigator', { configurable: true, value: dom.window.navigator })
globalThis.getComputedStyle = dom.window.getComputedStyle.bind(dom.window)
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0)
globalThis.cancelAnimationFrame = id => clearTimeout(id)

const [{ act, createElement }, { createRoot }, { App: KonstaApp }] = await Promise.all([
  import('react'),
  import('react-dom/client'),
  import('konsta/react'),
])
const vite = await createServer({
  appType: 'custom', server: { hmr: { port: 24681 }, middlewareMode: true },
})
const pageModule = await vite.ssrLoadModule('/src/platform-ai/PlatformAiPage.jsx').catch(() => ({}))
const PlatformAiPage = pageModule.default
const translations = structuredClone(en)
translations.pages.platformAi = {
  title: 'Platform AI', description: 'Manage shared AI.', loading: 'Loading platform AI...',
  apiSectionTitle: 'API configuration', apiSectionDescription: 'Manage platform API connections.',
  loadError: 'Could not load platform AI.', retry: 'Reload', runtimeUnavailable: 'Runtime unavailable',
  runtimeUnavailableDescription: 'Catalog metadata remains visible. Runtime actions are disabled.',
  addConnection: 'Add connection', apiConnection: 'Anthropic API', codexConnection: 'Codex CLI',
  createConnection: 'Create connection', connectionType: 'Connection type', baseUrl: 'Base URL',
  saveApiConfiguration: 'Save AI configuration',
  providerName: 'Provider name', requestUrl: 'Request URL', modelName: 'Model name',
  sortOrder: 'Sort order', cancel: 'Cancel', defaultDeny: 'New model access defaults to selected users.',
  enabled: 'Enabled', disabled: 'Disabled', credentialConfigured: 'Credential configured',
  credentialMissing: 'Credential missing', replaceCredential: 'Replace credential', apiKey: 'API key',
  saveCredential: 'Save credential', addOffering: 'Add model', modelKey: 'Model key',
  discoverModels: 'Discover models', discoveryUnavailable: 'Model discovery unavailable. Enter a model key.',
  createOffering: 'Create model', allUsers: 'All users', selectedUsers: 'Selected users',
  defaultAccessAria: 'Allow {{model}} for all users', connectionEnabledAria: 'Enable {{name}}',
  offeringEnabledAria: 'Enable {{model}}', deleteConnection: 'Delete connection',
  deleteOffering: 'Delete model', deleteConfirmTitle: 'Delete permanently?',
  deleteConnectionConfirmBody: 'Delete this connection permanently.',
  deleteOfferingConfirmBody: 'Delete this model permanently.', deleteConfirmAction: 'Delete permanently',
  saveError: 'Could not save.', emptyTitle: 'No platform AI connections',
  emptyDescription: 'Add Anthropic API or Codex CLI.', runtimeStatus: 'Runtime: {{status}}',
  emptyApiTitle: 'No API connections', emptyApiDescription: 'Add an Anthropic API connection.',
  runtime: translations.pages.platformAi.runtime,
}
const i18n = createInstance()
await i18n.use(initReactI18next).init({
  fallbackLng: 'en', interpolation: { escapeValue: false }, lng: 'en',
  resources: { en: { translation: translations } },
})

after(async () => {
  await vite.close()
  dom.window.close()
})

function pageData(overrides = {}) {
  const value = { runtimeStatus: 'AVAILABLE', runtimeAvailable: true, connections: [], ...overrides }
  return {
    ...value,
    connections: value.connections.map(connection => ({
      source: 'PLATFORM',
      ...connection,
      offerings: connection.offerings.map(offering => ({ source: 'PLATFORM', ...offering })),
    })),
  }
}

test('page ignores provider-shaped items unless source is PLATFORM', async () => {
  const page = await renderPage(api({ getPage: async () => pageData({ connections: [
    {
      connectionKey: 'user-codex', source: 'USER', kind: 'CLI',
      protocol: 'CODEX_APP_SERVER', enabled: true, offerings: [],
    },
    {
      connectionKey: 'platform-api', source: 'PLATFORM', kind: 'API',
      protocol: 'ANTHROPIC', enabled: true, offerings: [],
    },
  ] }) }))

  try {
    assert.ok(page.container.querySelector('[aria-label="Enable Anthropic API"]'))
    assert.equal(Boolean(page.container.querySelector('[aria-label="Enable Codex CLI"]')), false)
  } finally {
    await page.unmount()
  }
})

function api(overrides = {}) {
  return {
    createConnection: async () => {}, createOffering: async () => {},
    deleteConnection: async () => {}, deleteOffering: async () => {},
    discoverModels: async () => [], discoverModelsForConfiguration: async () => [],
    getPage: async () => pageData(),
    replaceCredentials: async () => {}, setDefaultAccess: async () => {},
    updateConnection: async () => {}, updateOffering: async () => {},
    ...overrides,
  }
}

function cliApi(overrides = {}) {
  return {
    cancelLogin: async () => ({ state: 'CANCELED', verificationUrl: '', userCode: '' }),
    getSnapshot: async () => ({
      enabled: true,
      runtimeStatus: 'AVAILABLE',
      login: { state: 'IDLE', verificationUrl: '', userCode: '' },
      globalChangeMaxDelaySeconds: 60,
    }),
    logoutAccount: async () => null,
    setEnabled: async () => null,
    startLogin: async () => ({ state: 'STARTING', verificationUrl: '', userCode: '' }),
    ...overrides,
  }
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

async function renderPage(apiValue, cliApiValue = cliApi()) {
  assert.equal(typeof PlatformAiPage, 'function', 'PlatformAiPage must exist')
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)
  await act(async () => {
    root.render(createElement(KonstaApp, { dark: false, theme: 'ios' },
      createElement(I18nextProvider, { i18n }, createElement(PlatformAiPage, {
        api: apiValue,
        cliApi: cliApiValue,
      }))))
    await Promise.resolve()
  })
  await flush()
  return { container, async unmount() { await act(async () => root.unmount()); container.remove() } }
}

function button(container, text) {
  return [...container.querySelectorAll('button')]
    .find(candidate => candidate.textContent.trim() === text)
}

function controlledPromise() {
  let resolve
  const promise = new Promise(resolvePromise => { resolve = resolvePromise })
  return { promise, resolve }
}

async function enter(input, value) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

test('platform AI page reuses CLI controls and keeps the connection dialog API-only', () => {
  assert.match(pageSource, /Card/)
  assert.match(pageSource, /List/)
  assert.match(pageSource, /ListItem/)
  assert.match(pageSource, /Button/)
  assert.match(pageSource, /<CodexAdminPage api={cliApi} embedded \/>/)
  assert.match(connectionDialogSource, /Dialog/)
  assert.match(connectionDialogSource, /AppListInput/)
  assert.match(offeringDialogSource, /Dialog/)
  assert.match(offeringDialogSource, /AppListInput/)
  assert.doesNotMatch(connectionDialogSource, /name=["']cliKey|label=.*cliKey/i)
  assert.doesNotMatch(connectionDialogSource, /CODEX_APP_SERVER|cliKey:\s*'codex'/)
  assert.match(connectionDialogSource, /connectionCreatePayload\('API'/)
})

test('API controls use the shared aligned platform content width', () => {
  assert.match(
    pageSource,
    /aria-labelledby="platform-ai-api-title" className="platform-ai-content-column"/,
  )
})

test('offline response renders safe metadata in ready state and disables runtime actions', async () => {
  const page = await renderPage(api({ getPage: async () => pageData({
    runtimeStatus: 'ERROR', runtimeAvailable: false,
    connections: [{
      connectionKey: 'platform-api-private', kind: 'API', protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
      enabled: true, sortOrder: 0,
      offerings: [{ offeringKey: 'private-offering', modelKey: 'claude-3-5-sonnet',
        enabled: true, defaultAccess: false, sortOrder: 0, runtimeStatus: 'ERROR' }],
    }],
  }) }))
  try {
    assert.match(page.container.textContent, /Runtime unavailable/)
    assert.match(page.container.textContent, /Runtime: Unavailable/)
    assert.match(page.container.textContent, /claude-3-5-sonnet/)
    assert.doesNotMatch(page.container.textContent, /Could not load platform AI/)
    assert.doesNotMatch(page.container.textContent, /platform-api-private|private-offering/)
    const apiSection = page.container.querySelector('[aria-labelledby="platform-ai-api-title"]')
    for (const control of apiSection.querySelectorAll('button, input')) {
      if (control.getAttribute('aria-label')?.includes('Reload')) continue
      assert.equal(control.disabled, true, `${control.textContent || control.getAttribute('aria-label')} must be disabled`)
    }
  } finally {
    await page.unmount()
  }
})

test('UNAVAILABLE runtime status is localized without rendering the raw enum', async () => {
  const page = await renderPage(api({ getPage: async () => pageData({ connections: [{
    connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
    baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
    enabled: true, sortOrder: 0, offerings: [{
      offeringKey: 'platform-model', modelKey: 'claude-one', enabled: true,
      defaultAccess: false, sortOrder: 0, runtimeStatus: 'UNAVAILABLE',
    }],
  }] }) }))
  try {
    assert.match(page.container.textContent, /Runtime: Temporarily unavailable/)
    assert.doesNotMatch(page.container.textContent, /UNAVAILABLE/)
  } finally {
    await page.unmount()
  }
})

test('new API connection uses fixed protocol and default-deny guidance', async () => {
  const creates = []
  const replacements = []
  const offerings = []
  const page = await renderPage(api({
    createConnection: async value => {
      creates.push(value)
      return { ...value, connectionKey: 'platform-api-new', offerings: [] }
    },
    replaceCredentials: async (...args) => replacements.push(args),
    createOffering: async (...args) => {
      offerings.push(args)
      return { offeringKey: 'new-model', modelKey: args[1], enabled: true, defaultAccess: false, sortOrder: 0 }
    },
  }))
  try {
    await act(async () => button(page.container, 'Add connection').click())
    assert.match(page.container.textContent, /defaults to selected users/)
    await enter(page.container.querySelector('#platform-ai-display-name'), 'Kimi')
    const baseUrl = page.container.querySelector('#platform-ai-base-url')
    await enter(baseUrl, 'https://api.anthropic.com')
    await enter(page.container.querySelector('#platform-ai-api-key'), 'secret')
    await enter(page.container.querySelector('#platform-ai-model-key'), 'kimi-k2')
    await act(async () => button(page.container, 'Save AI configuration').click())
    await flush()
    assert.deepEqual(creates, [{
      kind: 'API', protocol: 'ANTHROPIC', cliKey: null,
      displayName: 'Kimi', baseUrl: 'https://api.anthropic.com', sortOrder: 0,
    }])
    assert.deepEqual(replacements, [['platform-api-new', 'secret']])
    assert.deepEqual(offerings, [['platform-api-new', 'kimi-k2', 0]])
  } finally {
    await page.unmount()
  }
})

test('configured credential is replaced from an empty password input without echo', async () => {
  const replacements = []
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [{
      connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
      enabled: true, sortOrder: 0, offerings: [],
    }] }),
    replaceCredentials: async (...args) => replacements.push(args),
  }))
  try {
    assert.match(page.container.textContent, /Credential configured/)
    await act(async () => button(page.container, 'Replace credential').click())
    const secret = page.container.querySelector('input[type="password"]')
    assert.equal(secret.value, '')
    await enter(secret, 'replacement-secret')
    await act(async () => button(page.container, 'Save credential').click())
    await flush()
    assert.deepEqual(replacements, [['platform-api-one', 'replacement-secret']])
    assert.doesNotMatch(page.container.textContent, /replacement-secret/)
  } finally {
    await page.unmount()
  }
})

test('cancelled credential replacement clears the secret before reopening', async () => {
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [{
      connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
      enabled: true, sortOrder: 0, offerings: [],
    }] }),
  }))
  try {
    await act(async () => button(page.container, 'Replace credential').click())
    await enter(page.container.querySelector('input[type="password"]'), 'must-not-stay')
    await act(async () => button(page.container, 'Cancel').click())
    await act(async () => button(page.container, 'Replace credential').click())
    assert.equal(page.container.querySelector('input[type="password"]').value, '')
  } finally {
    await page.unmount()
  }
})

test('API connection supports several models, discovered selection and manual fallback', async () => {
  const creates = []
  const source = pageData({ connections: [{
    connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
    baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
    enabled: true, sortOrder: 0,
    offerings: [
      { offeringKey: 'one', modelKey: 'claude-one', enabled: true, defaultAccess: false, sortOrder: 0, runtimeStatus: 'AVAILABLE' },
      { offeringKey: 'two', modelKey: 'claude-two', enabled: true, defaultAccess: true, sortOrder: 1, runtimeStatus: 'AVAILABLE' },
    ],
  }] })
  let discoveryFails = false
  const page = await renderPage(api({
    getPage: async () => source,
    createOffering: async (...args) => creates.push(args),
    discoverModels: async () => {
      if (discoveryFails) throw new Error('offline')
      return [{ modelKey: 'claude-discovered' }]
    },
  }))
  try {
    assert.match(page.container.textContent, /claude-one/)
    assert.match(page.container.textContent, /claude-two/)
    await act(async () => button(page.container, 'Add model').click())
    await act(async () => button(page.container, 'Discover models').click())
    await flush()
    await act(async () => button(page.container, 'claude-discovered').click())
    await act(async () => button(page.container, 'Create model').click())
    await flush()
    assert.deepEqual(creates[0], ['platform-api-one', 'claude-discovered', 0])

    discoveryFails = true
    await act(async () => button(page.container, 'Add model').click())
    await act(async () => button(page.container, 'Discover models').click())
    await flush()
    assert.match(page.container.textContent, /discovery unavailable/)
    const model = page.container.querySelector('#platform-ai-model-key')
    await enter(model, 'claude-manual')
    await act(async () => button(page.container, 'Create model').click())
    await flush()
    assert.deepEqual(creates[1], ['platform-api-one', 'claude-manual', 0])
  } finally {
    await page.unmount()
  }
})

test('created offering is appended enabled with selected-user access by default', async () => {
  const creates = []
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [{
      connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
      enabled: true, sortOrder: 0, offerings: [],
    }] }),
    createOffering: async (...args) => {
      creates.push(args)
      return {
        offeringKey: 'platform-new-model', modelKey: args[1], enabled: true,
        defaultAccess: false, sortOrder: args[2], runtimeStatus: 'AVAILABLE',
      }
    },
  }))
  try {
    await act(async () => button(page.container, 'Add model').click())
    await enter(page.container.querySelector('#platform-ai-model-key'), 'claude-new')
    await act(async () => button(page.container, 'Create model').click())
    await flush()

    assert.deepEqual(creates, [['platform-api-one', 'claude-new', 0]])
    assert.match(page.container.textContent, /claude-new/)
    assert.match(page.container.textContent, /Selected users/)
    const enabledToggle = page.container.querySelector('[aria-label="Enable claude-new"]')
    const accessToggle = page.container.querySelector('[aria-label="Allow claude-new for all users"]')
    assert.equal((enabledToggle.matches('input') ? enabledToggle : enabledToggle.querySelector('input')).checked, true)
    assert.equal((accessToggle.matches('input') ? accessToggle : accessToggle.querySelector('input')).checked, false)
  } finally {
    await page.unmount()
  }
})

test('late model discovery from a closed connection cannot populate another connection', async () => {
  const discoveryA = controlledPromise()
  const discoveryB = controlledPromise()
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [
      { connectionKey: 'connection-a', kind: 'API', protocol: 'ANTHROPIC',
        baseUrl: 'https://a.test', credentialsConfigured: true, enabled: true,
        sortOrder: 0, offerings: [] },
      { connectionKey: 'connection-b', kind: 'API', protocol: 'ANTHROPIC',
        baseUrl: 'https://b.test', credentialsConfigured: true, enabled: true,
        sortOrder: 1, offerings: [] },
    ] }),
    discoverModels: async connectionKey => connectionKey === 'connection-a'
      ? discoveryA.promise
      : discoveryB.promise,
  }))
  try {
    const addModelButtons = () => [...page.container.querySelectorAll('button')]
      .filter(candidate => candidate.textContent.trim() === 'Add model')
    await act(async () => addModelButtons()[0].click())
    await act(async () => button(page.container, 'Discover models').click())
    await act(async () => button(page.container, 'Cancel').click())
    await act(async () => addModelButtons()[1].click())
    await act(async () => discoveryA.resolve([{ modelKey: 'model-from-a' }]))
    await flush()
    assert.doesNotMatch(page.container.textContent, /model-from-a/)

    await act(async () => button(page.container, 'Discover models').click())
    await act(async () => discoveryB.resolve([{ modelKey: 'model-from-b' }]))
    await flush()
    assert.match(page.container.textContent, /model-from-b/)
  } finally {
    await page.unmount()
  }
})

test('all-user toggle sends the offering key and updates its resolved state', async () => {
  const updates = []
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [{
      connectionKey: 'platform-api-one', kind: 'API', protocol: 'ANTHROPIC',
      baseUrl: 'https://api.anthropic.com', credentialsConfigured: true,
      enabled: true, sortOrder: 0, offerings: [{
        offeringKey: 'platform-model', modelKey: 'claude-one', enabled: true,
        defaultAccess: false, sortOrder: 0, runtimeStatus: 'AVAILABLE',
      }],
    }] }),
    setDefaultAccess: async (...args) => updates.push(args),
  }))
  try {
    const toggle = page.container.querySelector('[aria-label="Allow claude-one for all users"]')
    const input = toggle.matches('input') ? toggle : toggle.querySelector('input')
    await act(async () => input.click())
    await flush()
    assert.deepEqual(updates, [['platform-model', true]])
    assert.equal(input.checked, true)
    assert.match(page.container.textContent, /All users/)
  } finally {
    await page.unmount()
  }
})

test('CLI catalog rows never render generic connection, access or deletion controls', async () => {
  const page = await renderPage(api({
    getPage: async () => pageData({ connections: [{
      connectionKey: 'platform-codex', kind: 'CLI', protocol: 'CODEX_APP_SERVER',
      baseUrl: null, credentialsConfigured: false, enabled: true, sortOrder: 0,
      offerings: [{ offeringKey: 'platform-codex-cli', modelKey: null, enabled: true,
        defaultAccess: false, sortOrder: 0, runtimeStatus: 'AVAILABLE' }],
    }] }),
  }))
  try {
    assert.equal(page.container.querySelector('[aria-label="Toggle Codex globally"]'), null)
    const codexCard = button(page.container, 'CodexOpenAI Codex CLIAvailable')
    await act(async () => codexCard.click())
    assert.ok(page.container.querySelector('[aria-label="Toggle Codex globally"]'))
    await act(async () => codexCard.click())
    assert.equal(page.container.querySelector('[aria-label="Toggle Codex globally"]'), null)
    assert.equal(page.container.querySelector('[aria-label="Enable Codex CLI"]'), null)
    assert.equal(page.container.querySelector('[aria-label="Allow Codex CLI for all users"]'), null)
    assert.equal(button(page.container, 'Add model'), undefined)
    assert.equal(button(page.container, 'Delete model'), undefined)
    assert.equal(button(page.container, 'Delete connection'), undefined)
    assert.doesNotMatch(page.container.textContent, /Selected users/)

    await act(async () => button(page.container, 'Add connection').click())
    assert.equal(page.container.querySelector('select'), null)
  } finally {
    await page.unmount()
  }
})
