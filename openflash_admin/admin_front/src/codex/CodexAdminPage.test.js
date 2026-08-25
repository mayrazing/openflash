import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createInstance } from 'i18next'
import { I18nextProvider, initReactI18next } from 'react-i18next'
import { createServer } from 'vite'
import en from '../locales/en.json' with { type: 'json' }
import { createCodexCoordinator } from './state.js'

const appSource = await readFile(new URL('../App.jsx', import.meta.url), 'utf8')
const pageSource = await readFile(new URL('./CodexAdminPage.jsx', import.meta.url), 'utf8')
const styleSource = await readFile(new URL('../index.css', import.meta.url), 'utf8')
const locales = await Promise.all(['zh', 'en', 'fi', 'de'].map(async locale => (
  JSON.parse(await readFile(new URL(`../locales/${locale}.json`, import.meta.url), 'utf8'))
)))

test('Codex details stack until their content area is wide enough', () => {
  assert.match(pageSource, /className="admin-responsive-grid admin-responsive-grid--codex"/)
  assert.match(
    styleSource,
    /@container\s*\(min-width:\s*44rem\)[\s\S]*?\.admin-responsive-grid--codex\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1\.2fr\)\s+minmax\(340px,\s*0\.8fr\)/,
  )
})

test('CLI list and selected details use one aligned content width', () => {
  assert.match(
    styleSource,
    /\.platform-ai-content-column\s*\{[^}]*width:\s*calc\(100%\s*-\s*2rem\)[^}]*margin-inline-start:\s*0\.25rem/s,
  )
  assert.match(pageSource, /className="platform-ai-content-column !mx-0 !mb-5 !mt-0"/)
  assert.match(pageSource, /id="codex-cli-details" className="platform-ai-content-column"/)
})

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://admin.test/' })
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

const [{ act, createElement }, { createRoot }] = await Promise.all([
  import('react'),
  import('react-dom/client'),
])
const vite = await createServer({
  appType: 'custom', server: { hmr: { port: 24680 }, middlewareMode: true },
})
const { default: CodexAdminPage } = await vite.ssrLoadModule('/src/codex/CodexAdminPage.jsx')
const i18n = createInstance()
await i18n.use(initReactI18next).init({
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  lng: 'en',
  resources: { en: { translation: en } },
})

after(async () => {
  await vite.close()
  dom.window.close()
})

function snapshot(loginState, overrides = {}) {
  return {
    enabled: true,
    runtimeStatus: 'NOT_LOGGED_IN',
    login: { state: loginState, verificationUrl: '', userCode: '' },
    globalChangeMaxDelaySeconds: 60,
    ...overrides,
  }
}

function controlledPromise() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, reject, resolve }
}

function manualScheduler() {
  const scheduled = []
  return {
    clear(id) {
      const entry = scheduled.find(item => item.id === id)
      if (entry) entry.canceled = true
    },
    pending() {
      return scheduled.filter(item => !item.canceled && !item.ran)
    },
    runNext() {
      const entry = this.pending()[0]
      assert.ok(entry, 'expected a scheduled poll')
      entry.ran = true
      return entry.callback()
    },
    schedule(callback, delay) {
      const entry = { callback, canceled: false, delay, id: scheduled.length + 1, ran: false }
      scheduled.push(entry)
      return entry.id
    },
  }
}

function coordinatorFixture(apiOverrides = {}) {
  const scheduler = manualScheduler()
  const states = []
  const api = {
    cancelLogin: async () => ({ state: 'CANCELED', verificationUrl: '', userCode: '' }),
    getSnapshot: async () => snapshot('IDLE'),
    logoutAccount: async () => null,
    setEnabled: async () => null,
    startLogin: async () => ({ state: 'STARTING', verificationUrl: '', userCode: '' }),
    ...apiOverrides,
  }
  const coordinator = createCodexCoordinator({
    api,
    clearSchedule: id => scheduler.clear(id),
    onChange: state => states.push(state),
    schedule: (callback, delay) => scheduler.schedule(callback, delay),
  })
  return { api, coordinator, scheduler, states }
}

function buttonWithText(container, text) {
  return [...container.querySelectorAll('button')]
    .find(button => button.textContent.includes(text))
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

async function renderPage(snapshotValue, options = {}) {
  const scheduler = options.scheduler ?? manualScheduler()
  const api = {
    cancelLogin: async () => ({ state: 'CANCELED', verificationUrl: '', userCode: '' }),
    getSnapshot: async () => snapshotValue,
    logoutAccount: async () => null,
    setEnabled: async () => null,
    startLogin: async () => ({ state: 'STARTING', verificationUrl: '', userCode: '' }),
    ...options.api,
  }
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)

  await act(async () => {
    root.render(createElement(I18nextProvider, { i18n }, createElement(CodexAdminPage, {
      api,
      clearSchedule: id => scheduler.clear(id),
      schedule: (callback, delay) => scheduler.schedule(callback, delay),
    })))
    await Promise.resolve()
  })
  await flush()

  return {
    api,
    container,
    root,
    scheduler,
    async unmount() {
      await act(async () => root.unmount())
      container.remove()
    },
  }
}

test('STARTING and PENDING poll every 1000ms, then terminal state stops polling', async () => {
  const snapshots = [snapshot('STARTING'), snapshot('PENDING'), snapshot('SUCCEEDED')]
  const fixture = coordinatorFixture({ getSnapshot: async () => snapshots.shift() })

  await fixture.coordinator.mount()
  assert.equal(fixture.scheduler.pending()[0].delay, 1000)
  await fixture.scheduler.runNext()
  assert.equal(fixture.coordinator.getState().snapshot.login.state, 'PENDING')
  assert.equal(fixture.scheduler.pending()[0].delay, 1000)
  await fixture.scheduler.runNext()

  assert.equal(fixture.coordinator.getState().snapshot.login.state, 'SUCCEEDED')
  assert.equal(fixture.scheduler.pending().length, 0)
})

test('unmount cancels polling and ignores an in-flight poll result', async () => {
  const poll = controlledPromise()
  let reads = 0
  const fixture = coordinatorFixture({
    getSnapshot: async () => {
      reads += 1
      return reads === 1 ? snapshot('PENDING') : poll.promise
    },
  })
  await fixture.coordinator.mount()
  const runningPoll = fixture.scheduler.runNext()
  fixture.coordinator.unmount()
  poll.resolve(snapshot('SUCCEEDED'))
  await runningPoll

  assert.equal(fixture.coordinator.getState().snapshot.login.state, 'PENDING')
  assert.equal(fixture.scheduler.pending().length, 0)
})

test('login action lock rejects duplicate starts and duplicate cancels', async () => {
  const start = controlledPromise()
  const cancel = controlledPromise()
  let startCalls = 0
  let cancelCalls = 0
  const fixture = coordinatorFixture({
    startLogin: () => {
      startCalls += 1
      return start.promise
    },
    cancelLogin: () => {
      cancelCalls += 1
      return cancel.promise
    },
  })
  await fixture.coordinator.mount()

  const firstStart = fixture.coordinator.startLogin()
  assert.equal(await fixture.coordinator.startLogin(), false)
  start.resolve({ state: 'PENDING', verificationUrl: '', userCode: '' })
  assert.equal(await firstStart, true)

  const firstCancel = fixture.coordinator.cancelLogin()
  assert.equal(await fixture.coordinator.cancelLogin(), false)
  cancel.resolve({ state: 'CANCELED', verificationUrl: '', userCode: '' })
  assert.equal(await firstCancel, true)
  assert.equal(startCalls, 1)
  assert.equal(cancelCalls, 1)
})

test('account logout locks duplicates and fails closed before refreshing the snapshot', async () => {
  const logout = controlledPromise()
  const refresh = controlledPromise()
  let logoutCalls = 0
  let reads = 0
  const fixture = coordinatorFixture({
    getSnapshot: async () => {
      reads += 1
      return reads === 1
        ? snapshot('IDLE', { runtimeStatus: 'AVAILABLE' })
        : refresh.promise
    },
    logoutAccount: () => {
      logoutCalls += 1
      return logout.promise
    },
  })
  await fixture.coordinator.mount()

  assert.equal(typeof fixture.coordinator.logoutAccount, 'function')
  const running = fixture.coordinator.logoutAccount()
  assert.equal(await fixture.coordinator.logoutAccount(), false)
  assert.equal(fixture.coordinator.getState().accountBusy, true)

  logout.resolve()
  await Promise.resolve()
  await Promise.resolve()

  assert.equal(fixture.coordinator.getState().snapshot.runtimeStatus, 'NOT_LOGGED_IN')
  assert.equal(fixture.coordinator.getState().snapshot.login.state, 'IDLE')
  assert.equal(fixture.coordinator.getState().accountBusy, true)
  assert.equal(fixture.coordinator.getState().refreshBusy, true)

  refresh.resolve(snapshot('IDLE', { runtimeStatus: 'NOT_LOGGED_IN' }))
  assert.equal(await running, true)
  assert.equal(fixture.coordinator.getState().accountBusy, false)
  assert.equal(logoutCalls, 1)
})

test('failed account logout keeps the available snapshot and exposes a logout error', async () => {
  const fixture = coordinatorFixture({
    getSnapshot: async () => snapshot('IDLE', { runtimeStatus: 'AVAILABLE' }),
    logoutAccount: async () => {
      throw new Error('private runtime detail')
    },
  })
  await fixture.coordinator.mount()

  assert.equal(await fixture.coordinator.logoutAccount(), false)
  assert.equal(fixture.coordinator.getState().snapshot.runtimeStatus, 'AVAILABLE')
  assert.equal(fixture.coordinator.getState().error, 'logout')
  assert.equal(fixture.coordinator.getState().accountBusy, false)
})

test('global toggle and login have independent locks while each blocks duplicates', async () => {
  const toggle = controlledPromise()
  const start = controlledPromise()
  let toggleCalls = 0
  let startCalls = 0
  const fixture = coordinatorFixture({
    setEnabled: () => {
      toggleCalls += 1
      return toggle.promise
    },
    startLogin: () => {
      startCalls += 1
      return start.promise
    },
  })
  await fixture.coordinator.mount()

  const toggling = fixture.coordinator.setEnabled(false)
  const starting = fixture.coordinator.startLogin()
  assert.equal(await fixture.coordinator.setEnabled(true), false)
  assert.equal(await fixture.coordinator.startLogin(), false)
  assert.equal(fixture.coordinator.getState().snapshot.enabled, false)

  toggle.resolve()
  start.resolve({ state: 'STARTING', verificationUrl: '', userCode: '' })
  assert.equal(await toggling, true)
  assert.equal(await starting, true)
  assert.equal(toggleCalls, 1)
  assert.equal(startCalls, 1)
})

test('failed global toggle restores its previous value without changing login state', async () => {
  const fixture = coordinatorFixture({ setEnabled: async () => { throw new Error('failed') } })
  await fixture.coordinator.mount()

  assert.equal(await fixture.coordinator.setEnabled(false), false)
  assert.equal(fixture.coordinator.getState().snapshot.enabled, true)
  assert.equal(fixture.coordinator.getState().snapshot.login.state, 'IDLE')
  assert.equal(fixture.coordinator.getState().error, 'toggle')
})

test('refresh cannot overwrite optimistic or successful enabled value with stale GET', async () => {
  for (const resolvePutFirst of [false, true]) {
    const put = controlledPromise()
    const refresh = controlledPromise()
    let reads = 0
    const fixture = coordinatorFixture({
      getSnapshot: () => {
        reads += 1
        return reads === 1 ? Promise.resolve(snapshot('IDLE')) : refresh.promise
      },
      setEnabled: () => put.promise,
    })
    await fixture.coordinator.mount()

    const toggling = fixture.coordinator.setEnabled(false)
    const refreshing = fixture.coordinator.refresh()
    assert.equal(fixture.coordinator.getState().snapshot.enabled, false)

    if (resolvePutFirst) {
      put.resolve()
      assert.equal(await toggling, true)
      refresh.resolve(snapshot('IDLE', { enabled: true, runtimeStatus: 'DISABLED' }))
      assert.equal(await refreshing, true)
    } else {
      refresh.resolve(snapshot('IDLE', { enabled: true, runtimeStatus: 'DISABLED' }))
      assert.equal(await refreshing, true)
      assert.equal(fixture.coordinator.getState().snapshot.enabled, false)
      put.resolve()
      assert.equal(await toggling, true)
    }

    assert.equal(fixture.coordinator.getState().snapshot.enabled, false)
    assert.equal(fixture.coordinator.getState().snapshot.runtimeStatus, 'DISABLED')
    fixture.coordinator.unmount()
  }
})

test('manual refresh updates enabled when no toggle overlaps it', async () => {
  let reads = 0
  const fixture = coordinatorFixture({
    getSnapshot: async () => {
      reads += 1
      return snapshot('IDLE', { enabled: reads === 1 })
    },
  })
  await fixture.coordinator.mount()

  assert.equal(await fixture.coordinator.refresh(), true)
  assert.equal(fixture.coordinator.getState().snapshot.enabled, false)
})

test('legacy CLI route redirects to platform AI and the CLI page supports embedding', () => {
  assert.match(appSource, /path="cli" element={<Navigate to="\/platform-ai" replace \/>}/)
  assert.match(pageSource, /embedded = false/)
  assert.match(pageSource, /!embedded && <PageHeading \/>/)
})

test('locales cover every runtime and login state', () => {
  for (const locale of locales) {
    for (const state of ['AVAILABLE', 'NOT_INSTALLED', 'NOT_LOGGED_IN', 'DISABLED', 'ERROR']) {
      assert.ok(locale.pages.cli.codex.runtime[state], `missing runtime locale: ${state}`)
    }
    for (const state of ['IDLE', 'STARTING', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELED']) {
      assert.ok(locale.pages.cli.codex.login[state], `missing login locale: ${state}`)
    }
    for (const key of [
      'refreshStatus',
      'logoutAccount',
      'logoutConfirmTitle',
      'logoutConfirmBody',
      'logoutCancel',
      'logoutActionError',
    ]) {
      assert.equal(typeof locale.pages.cli.codex[key], 'string', `missing Codex locale: ${key}`)
    }
  }
})

test('available account requires confirmation before shared Codex logout', async () => {
  let logoutCalls = 0
  const page = await renderPage(snapshot('IDLE', { runtimeStatus: 'AVAILABLE' }), {
    api: {
      logoutAccount: async () => {
        logoutCalls += 1
      },
    },
  })

  assert.ok(buttonWithText(page.container, 'Refresh status'))
  const logoutButton = buttonWithText(page.container, 'Sign out of Codex')
  assert.ok(logoutButton)

  await act(async () => logoutButton.click())
  const dialog = page.container.querySelector('[role="alertdialog"]')
  assert.ok(dialog)
  assert.match(dialog.textContent, /shared Codex CLI account/i)
  const confirmButton = buttonWithText(dialog, 'Sign out of Codex')
  assert.match(confirmButton.className, /bg-app-danger-fill/)
  assert.match(confirmButton.className, /text-white/)
  assert.equal(logoutCalls, 0)

  await act(async () => buttonWithText(dialog, 'Cancel').click())
  assert.equal(page.container.querySelector('[role="alertdialog"]'), null)
  assert.equal(logoutCalls, 0)

  await page.unmount()
})

test('confirming shared Codex logout calls the API once and renders sign-in state', async () => {
  let logoutCalls = 0
  let reads = 0
  const page = await renderPage(snapshot('IDLE', { runtimeStatus: 'AVAILABLE' }), {
    api: {
      getSnapshot: async () => {
        reads += 1
        return reads === 1
          ? snapshot('IDLE', { runtimeStatus: 'AVAILABLE' })
          : snapshot('IDLE', { runtimeStatus: 'NOT_LOGGED_IN' })
      },
      logoutAccount: async () => {
        logoutCalls += 1
      },
    },
  })

  await act(async () => buttonWithText(page.container, 'Sign out of Codex').click())
  const dialog = page.container.querySelector('[role="alertdialog"]')
  await act(async () => buttonWithText(dialog, 'Sign out of Codex').click())
  await flush()

  assert.equal(logoutCalls, 1)
  assert.equal(page.container.querySelector('[role="alertdialog"]'), null)
  assert.ok(buttonWithText(page.container, 'Sign in to Codex CLI'))

  await page.unmount()
})

test('real DOM renders every runtime state with correct action availability', async () => {
  const cases = [
    ['AVAILABLE', 'Available', 'Refresh status', false],
    ['NOT_INSTALLED', 'Not installed', 'Sign in to Codex CLI', true],
    ['NOT_LOGGED_IN', 'Not signed in', 'Sign in to Codex CLI', false],
    ['DISABLED', 'Disabled', 'Sign in to Codex CLI', false],
    ['ERROR', 'Connection failed', 'Sign in to Codex CLI', true],
  ]

  for (const [runtimeStatus, visibleText, actionText, disabled] of cases) {
    const page = await renderPage(snapshot('IDLE', { runtimeStatus }))
    assert.match(page.container.textContent, new RegExp(visibleText))
    const action = buttonWithText(page.container, actionText)
    assert.ok(action, `${runtimeStatus} action must render`)
    assert.equal(action.disabled, disabled, `${runtimeStatus} action disabled`)
    assert.match(page.container.textContent, /global switch can take up to 60 seconds/i)
    await page.unmount()
  }
})

test('runtime ERROR is a ready degraded state and disables every runtime mutation', async () => {
  const page = await renderPage(snapshot('FAILED', { runtimeStatus: 'ERROR' }))
  try {
    assert.match(page.container.textContent, /Connection failed/)
    assert.equal(page.container.textContent.includes('Could not load Codex status.'), false)
    const toggle = page.container.querySelector('[aria-label="Toggle Codex globally"]')
    const input = toggle.matches('input') ? toggle : toggle.querySelector('input')
    assert.equal(input.disabled, true)
    assert.equal(buttonWithText(page.container, 'Sign in to Codex CLI').disabled, true)
  } finally {
    await page.unmount()
  }
})

test('real DOM renders every login state and exposes credentials only for PENDING', async () => {
  const cases = [
    ['IDLE', 'Start sign-in to show device verification details here.', 'Sign in to Codex CLI'],
    ['STARTING', 'Starting', 'Cancel sign-in'],
    ['PENDING', 'Waiting for sign-in', 'Cancel sign-in'],
    ['SUCCEEDED', 'Start sign-in to show device verification details here.', 'Sign in to Codex CLI'],
    ['FAILED', 'This sign-in attempt failed. Start again.', 'Sign in to Codex CLI'],
    ['EXPIRED', 'The device code expired. Start again.', 'Sign in to Codex CLI'],
    ['CANCELED', 'This sign-in attempt was canceled.', 'Sign in to Codex CLI'],
  ]

  for (const [loginState, visibleText, actionText] of cases) {
    const page = await renderPage(snapshot(loginState, {
      login: {
        state: loginState,
        userCode: '<img src=x onerror=alert(1)>',
        verificationUrl: 'https://example.test/device',
      },
    }))
    assert.match(page.container.textContent, new RegExp(visibleText))
    assert.ok(buttonWithText(page.container, actionText), `${loginState} action must render`)
    assert.equal(page.container.textContent.includes('<img src=x onerror=alert(1)>'), loginState === 'PENDING')
    assert.equal(Boolean(buttonWithText(page.container, 'Open verification page')), loginState === 'PENDING')
    assert.equal(page.container.querySelector('img'), null)
    await page.unmount()
  }
})

test('NOT_LOGGED_IN runtime overrides stale SUCCEEDED login and can start again', async () => {
  let startCalls = 0
  const page = await renderPage(snapshot('SUCCEEDED', { runtimeStatus: 'NOT_LOGGED_IN' }), {
    api: {
      startLogin: async () => {
        startCalls += 1
        return { state: 'STARTING', verificationUrl: '', userCode: '' }
      },
    },
  })

  assert.match(page.container.textContent, /Not signed in/)
  assert.equal(page.container.textContent.includes('Shared sign-in complete'), false)
  assert.equal(page.container.textContent.includes('Codex CLI shared sign-in is complete.'), false)
  assert.equal(Boolean(buttonWithText(page.container, 'Check again')), false)
  const startButton = buttonWithText(page.container, 'Sign in to Codex CLI')
  assert.ok(startButton)

  await act(async () => startButton.click())

  assert.equal(startCalls, 1)
  await page.unmount()
})

test('real DOM opens only the PENDING sanitized URL with isolation flags', async () => {
  const page = await renderPage(snapshot('PENDING', {
    login: {
      state: 'PENDING',
      userCode: 'SAFE-CODE',
      verificationUrl: 'https://example.test/device',
    },
  }))
  const opened = []
  const originalOpen = window.open
  window.open = (...args) => opened.push(args)

  await act(async () => buttonWithText(page.container, 'Open verification page').click())

  assert.deepEqual(opened, [['https://example.test/device', '_blank', 'noopener,noreferrer']])
  window.open = originalOpen
  await page.unmount()
})

test('real DOM keeps global toggle independent while login action is pending', async () => {
  const start = controlledPromise()
  const toggle = controlledPromise()
  let startCalls = 0
  let toggleCalls = 0
  const page = await renderPage(snapshot('IDLE'), {
    api: {
      setEnabled: () => {
        toggleCalls += 1
        return toggle.promise
      },
      startLogin: () => {
        startCalls += 1
        return start.promise
      },
    },
  })
  const loginButton = buttonWithText(page.container, 'Sign in to Codex CLI')
  const toggleControl = page.container.querySelector('[aria-label="Toggle Codex globally"]')
  const toggleInput = toggleControl.matches('input') ? toggleControl : toggleControl.querySelector('input')

  await act(async () => loginButton.click())
  assert.equal(loginButton.disabled, true)
  assert.equal(toggleInput.disabled, false)
  await act(async () => toggleInput.click())
  assert.equal(toggleInput.disabled, true)
  assert.equal(startCalls, 1)
  assert.equal(toggleCalls, 1)

  start.resolve({ state: 'STARTING', verificationUrl: '', userCode: '' })
  toggle.resolve()
  await flush()
  await page.unmount()
})

test('real unmount cancels timer and ignores in-flight polling response', async () => {
  const poll = controlledPromise()
  let reads = 0
  const scheduler = manualScheduler()
  const page = await renderPage(snapshot('PENDING'), {
    api: {
      getSnapshot: () => {
        reads += 1
        return reads === 1 ? Promise.resolve(snapshot('PENDING')) : poll.promise
      },
    },
    scheduler,
  })
  assert.equal(scheduler.pending()[0].delay, 1000)

  let runningPoll
  await act(async () => {
    runningPoll = scheduler.runNext()
    await Promise.resolve()
  })
  await page.unmount()
  assert.equal(page.container.childNodes.length, 0)
  assert.equal(scheduler.pending().length, 0)

  poll.resolve(snapshot('SUCCEEDED'))
  await runningPoll
  await flush()
  assert.equal(reads, 2)
  assert.equal(scheduler.pending().length, 0)
})
