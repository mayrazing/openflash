import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { chromium } from 'playwright'
import react from '@vitejs/plugin-react'
import { createServer } from 'vite'

let browser
let server
let baseUrl

before(async () => {
  server = await createServer({
    configFile: false,
    root: new URL('../..', import.meta.url).pathname,
    logLevel: 'error',
    server: { host: '127.0.0.1', port: 0 },
    plugins: [react(), aiSettingsHarnessPlugin()],
  })
  await server.listen()
  const address = server.httpServer.address()
  baseUrl = `http://127.0.0.1:${address.port}`
  browser = await chromium.launch({ headless: true })
})

after(async () => {
  await browser?.close()
  await server?.close()
})

test('renders personal and fixed platform rows with source marker and fixed permissions', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ active: true }),
      platformFixed({ providerKey: 'shared-provider' }),
    ],
  })
  t.after(() => context.close())

  const personalRow = providerRow(page, 'Personal AI')
  const platformRow = providerRow(page, 'GPT-5.4')
  assert.match(await personalRow.innerText(), /personal-model/)
  assert.doesNotMatch(await personalRow.innerText(), /Platform provided/)
  assert.match(await platformRow.innerText(), /GPT-5\.4 \(Platform provided\)/)
  assert.equal(await platformRow.getByRole('button', { name: 'Edit' }).count(), 0)
  assert.equal(await platformRow.getByRole('button', { name: 'Delete provider' }).count(), 0)
})

test('editor appears after its provider row and before the next provider', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ id: 'USER:alpha', providerKey: 'alpha', displayName: 'Alpha' }),
      personalProvider({ id: 'USER:beta', providerKey: 'beta', displayName: 'Beta', active: true }),
    ],
  })
  t.after(() => context.close())

  const alphaRow = providerRow(page, 'Alpha')
  const betaRow = providerRow(page, 'Beta')
  await click(alphaRow.getByRole('button', { name: 'Edit' }))
  const editor = page.getByLabel('Provider name')
  await editor.waitFor()

  const editorHandle = await editor.elementHandle()
  const betaHandle = await betaRow.elementHandle()
  const order = await alphaRow.evaluate((alpha, [editorElement, beta]) => ({
    editorAfterAlpha: Boolean(alpha.compareDocumentPosition(editorElement) & Node.DOCUMENT_POSITION_FOLLOWING),
    editorBeforeBeta: Boolean(editorElement.compareDocumentPosition(beta) & Node.DOCUMENT_POSITION_FOLLOWING),
  }), [editorHandle, betaHandle])
  assert.deepEqual(order, { editorAfterAlpha: true, editorBeforeBeta: true })
})

test('new provider editor appears before existing provider rows', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true })],
  })
  t.after(() => context.close())

  await click(page.getByRole('button', { name: '+ New AI config' }))
  const editor = page.getByLabel('Provider name')
  const firstRow = providerRow(page, 'Personal AI')
  await editor.waitFor()

  const firstRowHandle = await firstRow.elementHandle()
  const editorBeforeFirstRow = await editor.evaluate((editorElement, row) => (
    Boolean(editorElement.compareDocumentPosition(row) & Node.DOCUMENT_POSITION_FOLLOWING)
  ), firstRowHandle)
  assert.equal(editorBeforeFirstRow, true)
})

test('row id keeps colliding personal and platform providers distinct during edit and activation', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ id: 'USER:shared', providerKey: 'shared', active: true }),
      platformCli({ id: 'PLATFORM:shared-cli', providerKey: 'shared', offeringKey: 'shared-cli' }),
    ],
    catalogs: { 'shared-cli': platformCatalog('gpt-platform') },
  })
  t.after(() => context.close())

  const platformRow = providerRow(page, 'gpt-platform (Platform provided)')
  await click(platformRow.getByRole('button', { name: 'Edit' }))
  await page.getByLabel('Reasoning effort').waitFor()
  assert.equal(await page.getByLabel('Provider name').count(), 0)

  await click(page.getByRole('button', { name: 'Cancel' }))
  await click(platformRow.getByRole('button', { name: 'Set active' }))
  await waitForCall(page, 'activatePlatformOffering')
  assert.deepEqual(await callArgs(page, 'activatePlatformOffering'), [['shared-cli']])
  assert.deepEqual(await callArgs(page, 'activateAiProvider'), [])
  assert.equal(await page.getByText('Active', { exact: true }).count(), 1)
})

test('each platform CLI edit loads its own offering catalog', async t => {
  const { context, page } = await openScenario({
    providers: [
      platformCli({ id: 'PLATFORM:first', providerKey: 'first', offeringKey: 'first', displayName: 'First CLI', model: 'gpt-first', active: true }),
      platformCli({ id: 'PLATFORM:second', providerKey: 'second', offeringKey: 'second', displayName: 'Second CLI', model: 'gpt-second' }),
    ],
    catalogs: {
      first: platformCatalog('gpt-first'),
      second: platformCatalog('gpt-second'),
    },
  })
  t.after(() => context.close())

  await click(providerRow(page, 'gpt-second (Platform provided)').getByRole('button', { name: 'Edit' }))
  await waitForCall(page, 'getPlatformModels', 2)
  const catalogCalls = await callArgs(page, 'getPlatformModels')
  assert.equal(catalogCalls.at(-1)[0], 'second')
  assert.equal(await page.getByLabel('Model').inputValue(), 'gpt-second')
})

test('direct activation validates each platform CLI against its own offering catalog', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ active: true }),
      platformCli({ id: 'PLATFORM:first', offeringKey: 'first', displayName: 'First CLI', model: 'gpt-first' }),
      platformCli({ id: 'PLATFORM:second', offeringKey: 'second', displayName: 'Second CLI', model: 'gpt-second', reasoningEffort: 'high' }),
    ],
    catalogs: {
      first: platformCatalog('gpt-first', ['low']),
      second: platformCatalog('gpt-second', ['high']),
    },
  })
  t.after(() => context.close())

  await waitForCall(page, 'getPlatformModels')
  await click(providerRow(page, 'gpt-first (Platform provided)').getByRole('button', { name: 'Set active' }))
  await waitForCall(page, 'activatePlatformOffering')
  await click(providerRow(page, 'gpt-second (Platform provided)').getByRole('button', { name: 'Set active' }))
  await waitForCall(page, 'activatePlatformOffering', 2)

  assert.deepEqual(await callArgs(page, 'activatePlatformOffering'), [['first'], ['second']])
  assert.deepEqual(await callArgs(page, 'getPlatformModels'), [['first'], ['first'], ['second']])
})

test('platform CLI editor exposes only model and effort fields with selected model marker', async t => {
  const { context, page } = await openScenario({
    providers: [platformCli({ active: true, model: 'gpt-chosen', reasoningEffort: 'high' })],
    catalogs: { 'platform-cli': platformCatalog('gpt-chosen', ['low', 'high']) },
  })
  t.after(() => context.close())

  const row = providerRow(page, 'gpt-chosen (Platform provided)')
  assert.doesNotMatch(await row.innerText(), /Platform CLI/)
  assert.match(await row.innerText(), /Reasoning effort: High/)
  await click(row.getByRole('button', { name: 'Edit' }))
  await page.getByLabel('Model').waitFor()
  assert.equal(await page.getByLabel('Model').inputValue(), 'gpt-chosen')
  assert.equal(await page.getByLabel('Reasoning effort').inputValue(), 'high')
  assert.equal(await page.getByLabel('Provider name').count(), 0)
  assert.equal(await page.getByLabel(/Base URL/).count(), 0)
  assert.equal(await page.getByLabel('API key').count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Delete provider' }).count(), 0)
})

test('every unavailable or denied platform row blocks activation while personal editing stays available', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ active: true }),
      platformFixed({ id: 'PLATFORM:offline', offeringKey: 'offline', displayName: 'Offline fixed', runtimeStatus: 'ERROR' }),
      platformFixed({ id: 'PLATFORM:denied', offeringKey: 'denied', displayName: 'Denied fixed', accessGranted: false }),
      platformCli({ id: 'PLATFORM:offline-cli', offeringKey: 'offline-cli', displayName: 'Offline CLI', runtimeStatus: 'NOT_INSTALLED' }),
    ],
  })
  t.after(() => context.close())

  for (const name of ['Offline fixed', 'Denied fixed', 'gpt-platform (Platform provided)']) {
    assert.equal(await providerRow(page, name).getByRole('button', { name: 'Set active' }).count(), 0)
  }
  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  await page.getByLabel('Provider name').waitFor()
  assert.equal(await page.getByLabel('Provider name').inputValue(), 'Personal AI')
})

test('personal Anthropic discovery requires and saves a supported reasoning effort', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true })],
    discoveredModels: [{
      id: 'personal-model', name: 'Personal model',
      supportedReasoningEfforts: ['low', 'high', 'max'],
    }],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  await click(page.getByRole('button', { name: 'Fetch models' }))
  await page.getByLabel('Reasoning effort').selectOption('high')
  await click(page.getByRole('button', { name: 'Save AI Config' }))
  await waitForCall(page, 'saveAiProvider')

  assert.equal((await callArgs(page, 'saveAiProvider'))[0][1].reasoningEffort, 'high')
  await page.getByText(/Reasoning effort: High/).waitFor()
})

test('personal Anthropic edit preserves saved effort before models are fetched again', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true, reasoningEffort: 'high' })],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  await page.getByLabel('Provider name').fill('Renamed AI')
  await click(page.getByRole('button', { name: 'Save AI Config' }))
  await waitForCall(page, 'saveAiProvider')

  assert.equal((await callArgs(page, 'saveAiProvider'))[0][1].reasoningEffort, 'high')
})

test('personal Anthropic editor shows effort before model capabilities are fetched', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true, reasoningEffort: null })],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  const effort = page.getByLabel('Reasoning effort')
  await effort.selectOption('high')
  await click(page.getByRole('button', { name: 'Save AI Config' }))
  await waitForCall(page, 'saveAiProvider')

  assert.equal((await callArgs(page, 'saveAiProvider'))[0][1].reasoningEffort, 'high')
})

test('personal save completion cannot overwrite a newer create selection', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true })],
    deferred: ['saveAiProvider'],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  await page.getByLabel('Provider name').fill('Stale save')
  await click(page.getByRole('button', { name: 'Save AI Config' }))
  await waitForCall(page, 'saveAiProvider')
  await click(page.getByRole('button', { name: '+ New AI config' }))
  assert.equal(await page.getByLabel('Provider name').inputValue(), '')

  await resolveNext(page, 'saveAiProvider')
  await page.waitForTimeout(100)
  assert.equal(await page.getByLabel('Provider name').inputValue(), '')
})

test('personal activation completion cannot replace a newer edit selection', async t => {
  const { context, page } = await openScenario({
    providers: [
      personalProvider({ id: 'USER:alpha', providerKey: 'alpha', displayName: 'Alpha', active: false }),
      personalProvider({ id: 'USER:beta', providerKey: 'beta', displayName: 'Beta', active: true }),
    ],
    deferred: ['activateAiProvider'],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Alpha').getByRole('button', { name: 'Set active' }))
  await waitForCall(page, 'activateAiProvider')
  await click(providerRow(page, 'Beta').getByRole('button', { name: 'Edit' }))
  assert.equal(await page.getByLabel('Provider name').inputValue(), 'Beta')

  await resolveNext(page, 'activateAiProvider')
  await page.waitForTimeout(100)
  assert.equal(await page.getByLabel('Provider name').inputValue(), 'Beta')
})

test('personal delete completion cannot close a newer create form', async t => {
  const { context, page } = await openScenario({
    providers: [personalProvider({ active: true })],
    deferred: ['deleteAiProvider'],
  })
  t.after(() => context.close())

  await click(providerRow(page, 'Personal AI').getByRole('button', { name: 'Edit' }))
  await click(page.getByRole('button', { name: 'Delete provider' }))
  await click(page.getByRole('button', { name: 'Delete', exact: true }))
  await waitForCall(page, 'deleteAiProvider')
  await click(page.getByLabel(/Delete this AI config/).getByRole('button', { name: 'Cancel' }))
  await click(page.getByRole('button', { name: '+ New AI config' }))
  assert.equal(await page.getByLabel('Provider name').inputValue(), '')

  await resolveNext(page, 'deleteAiProvider')
  await page.waitForTimeout(100)
  assert.equal(await page.getByLabel('Provider name').inputValue(), '')
})

test('all locales retain a parenthesized platform source marker', async () => {
  for (const name of ['zh', 'en', 'fi', 'de']) {
    const locale = JSON.parse(await readFile(new URL(`../locales/${name}.json`, import.meta.url), 'utf8'))
    assert.match(locale.settings.platformProvidedSuffix, /^\(.+\)$/)
  }
})

function personalProvider(overrides = {}) {
  return {
    id: 'USER:personal', source: 'USER', kind: 'API_KEY', providerKey: 'personal', offeringKey: null,
    displayName: 'Personal AI', baseUrl: 'https://personal.test/anthropic', apiKeyConfigured: true,
    model: 'personal-model', reasoningEffort: null, active: false, runtimeStatus: null,
    editable: true, deletable: true, accessGranted: true, ...overrides,
  }
}

function platformFixed(overrides = {}) {
  return {
    id: 'PLATFORM:fixed', source: 'PLATFORM', kind: 'API', providerKey: 'platform-fixed',
    offeringKey: 'platform-fixed', displayName: 'GPT-5.4', model: 'GPT-5.4', reasoningEffort: null,
    active: false, runtimeStatus: 'AVAILABLE', editable: false, deletable: false, accessGranted: true,
    ...overrides,
  }
}

function platformCli(overrides = {}) {
  return {
    id: 'PLATFORM:platform-cli', source: 'PLATFORM', kind: 'CLI', providerKey: 'platform-cli',
    offeringKey: 'platform-cli', displayName: 'Platform CLI', model: 'gpt-platform', reasoningEffort: 'low',
    active: false, runtimeStatus: 'AVAILABLE', editable: true, deletable: false, accessGranted: true,
    ...overrides,
  }
}

function platformCatalog(model, efforts = ['low']) {
  return {
    runtimeStatus: 'AVAILABLE',
    models: [{
      id: model, model, displayName: model, defaultModel: true,
      defaultReasoningEffort: efforts[0],
      supportedReasoningEfforts: efforts.map(reasoningEffort => ({ reasoningEffort })),
    }],
  }
}

async function openScenario(scenario) {
  const context = await browser.newContext()
  // CI 冷启动 (vite dev server + chromium) 可能挤占首个用例的等待窗口,
  // 3s 在慢机必超时; 断言目标不变, 只放宽单步等待上限.
  context.setDefaultTimeout(15000)
  await context.addInitScript(installHarness, scenario)
  const page = await context.newPage()
  await page.goto(baseUrl)
  await page.getByText('AI Configuration', { exact: true }).waitFor()
  if (scenario.providers.length > 0) await page.locator('li').first().waitFor()
  return { context, page }
}

function providerRow(page, displayName) {
  return page.locator('li').filter({ has: page.getByText(displayName, { exact: true }) }).first()
}

async function click(locator) {
  await locator.evaluate(node => node.click())
}

async function waitForCall(page, name, count = 1) {
  await page.waitForFunction(([callName, expected]) => (
    window.__aiHarness.calls.filter(call => call.name === callName).length >= expected
  ), [name, count])
}

async function callArgs(page, name) {
  return page.evaluate(callName => (
    window.__aiHarness.calls.filter(call => call.name === callName).map(call => call.args)
  ), name)
}

async function resolveNext(page, name) {
  const resolved = await page.evaluate(callName => window.__aiHarness.resolveNext(callName), name)
  assert.equal(resolved, true, `No pending ${name} call`)
}

function installHarness(initialScenario) {
  const scenario = structuredClone(initialScenario)
  const deferred = new Set(scenario.deferred || [])
  const pending = []
  const calls = []
  const clone = value => value === undefined ? undefined : structuredClone(value)

  function complete(name, args) {
    if (name === 'getAiProviders') return clone(scenario.providers)
    if (name === 'discoverAiModels') return clone(scenario.discoveredModels || [])
    if (name === 'getPlatformModels') {
      return clone(scenario.catalogs?.[args[0]] || { runtimeStatus: 'ERROR', models: [] })
    }
    if (name === 'activateAiProvider') {
      scenario.providers.forEach(provider => {
        provider.active = provider.source === 'USER' && provider.providerKey === args[0]
      })
    }
    if (name === 'activatePlatformOffering') {
      scenario.providers.forEach(provider => {
        provider.active = provider.source === 'PLATFORM' && provider.offeringKey === args[0]
      })
    }
    if (name === 'saveAiProvider') {
      const provider = scenario.providers.find(row => row.source === 'USER' && row.providerKey === args[0])
      if (provider) Object.assign(provider, args[1])
    }
    if (name === 'deleteAiProvider') {
      scenario.providers = scenario.providers.filter(row => row.providerKey !== args[0] || row.source !== 'USER')
    }
    return null
  }

  window.__aiHarness = {
    calls,
    invoke(name, args) {
      calls.push({ name, args: clone(args) })
      if (!deferred.has(name)) return Promise.resolve(complete(name, args))
      return new Promise((resolve, reject) => pending.push({ name, args, resolve, reject }))
    },
    resolveNext(name) {
      const index = pending.findIndex(call => call.name === name)
      if (index < 0) return false
      const call = pending.splice(index, 1)[0]
      call.resolve(complete(call.name, call.args))
      return true
    },
  }
}

function aiSettingsHarnessPlugin() {
  const entryId = '\0ai-settings-test-entry'
  const apiId = '\0ai-settings-api-mock'
  const soundId = '\0ai-settings-sound-mock'
  return {
    name: 'ai-settings-test-harness',
    enforce: 'pre',
    configureServer(viteServer) {
      viteServer.middlewares.use((request, response, next) => {
        if (request.url !== '/') return next()
        response.setHeader('Content-Type', 'text/html')
        response.end(`
          <div id="root"></div>
          <script type="module">
            import RefreshRuntime from '/@react-refresh'
            RefreshRuntime.injectIntoGlobalHook(window)
            window.$RefreshReg$ = () => {}
            window.$RefreshSig$ = () => type => type
            window.__vite_plugin_react_preamble_installed__ = true
          </script>
          <script type="module" src="/__ai-test-entry.jsx"></script>
        `)
      })
    },
    resolveId(source, importer) {
      if (source === '/__ai-test-entry.jsx') return entryId
      if (source === './api.js' && importer?.endsWith('/src/ai/AiSettingsSection.jsx')) return apiId
      if (source.includes('soundEngine') && importer?.includes('/src/')) return soundId
      return null
    },
    load(id) {
      if (id === entryId) return `
        import React from 'react'
        import { createRoot } from 'react-dom/client'
        import i18next from 'i18next'
        import { I18nextProvider } from 'react-i18next'
        import en from '/src/locales/en.json'
        import AiSettingsSection from '/src/ai/AiSettingsSection.jsx'
        const translations = i18next.createInstance()
        await translations.init({ lng: 'en', resources: { en: { translation: en } } })
        createRoot(document.getElementById('root')).render(
          React.createElement(I18nextProvider, { i18n: translations }, React.createElement(AiSettingsSection)),
        )
      `
      if (id === apiId) return `
        const call = (name, args) => window.__aiHarness.invoke(name, args)
        export const getAiProviders = (...args) => call('getAiProviders', args)
        export const getCodexModels = (...args) => call('getCodexModels', args)
        export const getPlatformModels = (...args) => call('getPlatformModels', args)
        export const saveCodexConfig = (...args) => call('saveCodexConfig', args)
        export const savePlatformPreference = (...args) => call('savePlatformPreference', args)
        export const saveAiProvider = (...args) => call('saveAiProvider', args)
        export const createAiProvider = (...args) => call('createAiProvider', args)
        export const activateAiProvider = (...args) => call('activateAiProvider', args)
        export const activatePlatformOffering = (...args) => call('activatePlatformOffering', args)
        export const deleteAiProvider = (...args) => call('deleteAiProvider', args)
        export const discoverAiModels = (...args) => call('discoverAiModels', args)
      `
      if (id === soundId) return `export const withGenericClick = handler => handler`
      return null
    },
  }
}
