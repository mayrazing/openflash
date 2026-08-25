import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createInstance } from 'i18next'
import { I18nextProvider, initReactI18next } from 'react-i18next'
import { createServer } from 'vite'
import en from '../locales/en.json' with { type: 'json' }
import { createUsersCoordinator } from './state.js'

const pageSource = await readFile(new URL('./UsersPage.jsx', import.meta.url), 'utf8')
const appSource = await readFile(new URL('../App.jsx', import.meta.url), 'utf8')
const styleSource = await readFile(new URL('../index.css', import.meta.url), 'utf8')
const locales = await Promise.all(['zh', 'en', 'fi', 'de'].map(async locale => (
  JSON.parse(await readFile(new URL(`../locales/${locale}.json`, import.meta.url), 'utf8'))
)))

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://admin.test/users' })
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
  appType: 'custom', server: { hmr: { port: 24679 }, middlewareMode: true },
})
const { default: UsersPage } = await vite.ssrLoadModule('/src/users/UsersPage.jsx')
const testTranslation = structuredClone(en)
Object.assign(testTranslation.pages.users, {
  accountActionError: 'Account action failed.',
  activeStatus: 'Active',
  bannedStatus: 'Banned',
  banUser: 'Ban account',
  deleteCancel: 'Cancel',
  deleteConfirmAction: 'Delete',
  deleteConfirmBody: 'Delete {{user}} permanently.',
  deleteConfirmTitle: 'Permanently delete account?',
  deleteUser: 'Delete account',
  deleting: 'Deleting...',
  selfAccountActionDisabled: 'You cannot ban or delete your current account.',
  selfAccountMutationError: 'You cannot ban or delete your current account.',
  unbanUser: 'Unban account',
  platformProvided: 'Platform provided',
  runtimeUnavailable: 'Runtime unavailable',
  runtimeUnavailableDescription: 'Platform access changes are disabled.',
  userServiceUnavailableError: 'User service is not running.',
})
const i18n = createInstance()
await i18n.use(initReactI18next).init({
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  lng: 'en',
  resources: { en: { translation: testTranslation } },
})

after(async () => {
  await vite.close()
  dom.window.close()
})

function user(overrides = {}) {
  return {
    banned: false,
    id: 7,
    username: 'amy',
    nickname: 'Amy',
    role: 'USER',
    cliAccess: { 'codex-cli': false },
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

function loadUsers(coordinator, users) {
  const request = coordinator.startListRequest()
  assert.equal(request.succeed(users), true)
}

test('users route renders the real user management page', () => {
  assert.match(appSource, /import UsersPage from ['"]\.\/users\/UsersPage\.jsx['"]/)
  assert.match(appSource, /path="users" element={<UsersPage currentAdminId={admin\.id}\s*\/>}/)
  assert.doesNotMatch(appSource, /path="users" element={<PlaceholderPage/)
})

test('page loads a searchable list and renders loading, error and empty states', () => {
  assert.match(pageSource, /listUsers\(query\)/)
  assert.match(pageSource, /pages\.users\.searchLabel/)
  const searchInput = pageSource.match(/<AppListInput[\s\S]*?pages\.users\.searchPlaceholder[\s\S]*?\/>/)?.[0]
  assert.ok(searchInput)
  assert.doesNotMatch(searchInput, /\boutline\b/)
  assert.match(pageSource, /pages\.users\.loading/)
  assert.match(pageSource, /pages\.users\.loadError/)
  assert.match(pageSource, /pages\.users\.retry/)
  assert.match(pageSource, /pages\.users\.emptyTitle/)
  assert.match(pageSource, /pages\.users\.emptyDescription/)
})

test('each row identifies the user and opens dynamic platform AI access management', () => {
  assert.match(pageSource, /user\.nickname\s*\|\|\s*user\.username/)
  assert.match(pageSource, /`@\$\{user\.username\}`/)
  assert.match(pageSource, />USER<\/SegmentedButton>/)
  assert.match(pageSource, />ADMIN<\/SegmentedButton>/)
  assert.match(pageSource, /pages\.users\.cliAccess/)
  assert.match(pageSource, /pages\.users\.manageCliAccess/)
  assert.match(pageSource, /offering\.cliKey/)
  assert.match(pageSource, /user\.cliAccess/)
  assert.match(pageSource, /<Dialog/)
  assert.doesNotMatch(pageSource, /user\.codexAccess|updateUserCodexAccess/)
})

test('successful mutation stays authoritative over a GET started before PUT confirmation', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'role', 'ADMIN')
  const mutationResult = mutation.run(() => write.promise)
  const staleList = coordinator.startListRequest()

  write.resolve()
  assert.deepEqual(await mutationResult, { status: 'succeeded' })
  assert.equal(staleList.succeed([user({ role: 'USER' })]), true)

  assert.equal(coordinator.getState().users[0].role, 'ADMIN')
})

test('failed mutation restores a newer GET-confirmed value instead of its captured old value', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'role', 'ADMIN')
  const mutationResult = mutation.run(() => write.promise)
  loadUsers(coordinator, [user({ role: 'ADMIN' })])

  const error = new Error('write failed')
  write.reject(error)
  assert.deepEqual(await mutationResult, { error, status: 'failed' })

  assert.equal(coordinator.getState().users[0].role, 'ADMIN')
})

test('failed mutation without a newer GET restores the prior confirmed value', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'cliAccess', true, 'codex-cli')
  const mutationResult = mutation.run(() => write.promise)

  write.reject(new Error('write failed'))
  assert.equal((await mutationResult).status, 'failed')
  assert.equal(coordinator.getState().users[0].cliAccess['codex-cli'], false)
})

test('failed platform offering override restores the prior resolved access', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user({ offeringAccess: { 'platform-model': false } })])
  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'offeringAccess', true, 'platform-model')
  const mutationResult = mutation.run(() => write.promise)

  write.reject(new Error('write failed'))
  assert.equal((await mutationResult).status, 'failed')
  assert.equal(coordinator.getState().users[0].offeringAccess['platform-model'], false)
})

test('older GET results and a second same-user mutation are rejected', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const olderList = coordinator.startListRequest()
  const newerList = coordinator.startListRequest()

  assert.equal(olderList.succeed([user({ role: 'ADMIN' })]), false)
  assert.equal(coordinator.getState().users[0].role, 'USER')
  assert.equal(newerList.succeed([user({ role: 'USER' })]), true)

  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'role', 'ADMIN')
  const mutationResult = mutation.run(() => write.promise)
  assert.equal(coordinator.startMutation(7, 'cliAccess', true, 'codex-cli'), null)
  write.resolve()
  await mutationResult
})

test('failed ban restores the prior status', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const mutation = coordinator.startMutation(7, 'banned', true)

  assert.ok(mutation)
  const mutationResult = mutation.run(() => write.promise)
  write.reject(new Error('write failed'))

  assert.equal((await mutationResult).status, 'failed')
  assert.equal(coordinator.getState().users[0].banned, false)
})

test('confirmed deletion stays removed over an older GET result', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const removal = coordinator.startMutation(7, 'deleted', true)

  assert.ok(removal)
  const result = removal.run(() => write.promise)
  const staleList = coordinator.startListRequest()

  write.resolve()
  assert.deepEqual(await result, { status: 'succeeded' })
  assert.equal(staleList.succeed([user()]), true)
  assert.equal(coordinator.getState().users[0].deleted, true)
})

test('failed deletion restores the row', async () => {
  const coordinator = createUsersCoordinator()
  loadUsers(coordinator, [user()])
  const write = controlledPromise()
  const removal = coordinator.startMutation(7, 'deleted', true)
  const result = removal.run(() => write.promise)

  write.reject(new Error('write failed'))
  assert.equal((await result).status, 'failed')
  assert.equal(coordinator.getState().users[0].deleted, undefined)
})

test('both row control groups use the same saving state', () => {
  assert.ok((pageSource.match(/disabled={isSaving/g)?.length ?? 0) >= 5)
})

function apiResponse(data = null, { code = 200, ok = true, status = 200 } = {}) {
  return {
    ok,
    status,
    text: async () => JSON.stringify({ code, data }),
  }
}

function buttonWithText(container, text) {
  return [...container.querySelectorAll('button')]
    .find(button => button.textContent.trim() === text)
}

function lastButtonWithText(container, text) {
  return [...container.querySelectorAll('button')]
    .filter(button => button.textContent.trim() === text)
    .at(-1)
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

async function renderUsersPage(fetchImpl, currentAdminId = 7) {
  globalThis.fetch = fetchImpl
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)

  await act(async () => {
    root.render(createElement(
      KonstaApp,
      { dark: false, theme: 'ios' },
      createElement(I18nextProvider, { i18n }, createElement(UsersPage, { currentAdminId })),
    ))
    await Promise.resolve()
  })
  await flush()

  return {
    container,
    async unmount() {
      await act(async () => root.unmount())
      container.remove()
    },
  }
}

function listedUsers() {
  return {
    clis: [],
    users: [
      user(),
      user({ banned: true, id: 8, nickname: 'Bob', username: 'bob' }),
    ],
  }
}

test('rendered rows show account status and disable destructive self actions', async () => {
  const page = await renderUsersPage(async () => apiResponse(listedUsers()))

  try {
    assert.match(page.container.textContent, /Active/)
    assert.match(page.container.textContent, /Banned/)
    const banButtons = [...page.container.querySelectorAll('button')]
      .filter(button => ['Ban account', 'Unban account'].includes(button.textContent.trim()))
    const deleteButtons = [...page.container.querySelectorAll('button')]
      .filter(button => button.textContent.trim() === 'Delete account')

    assert.equal(banButtons.length, 2)
    assert.equal(deleteButtons.length, 2)
    assert.equal(banButtons[0].disabled, true)
    assert.equal(deleteButtons[0].disabled, true)
    assert.match(banButtons[0].getAttribute('title'), /current account/)
    assert.equal(banButtons[1].disabled, false)
    assert.equal(deleteButtons[1].disabled, false)
  } finally {
    await page.unmount()
  }
})

test('ban and confirmed delete use one row mutation lock and remove a deleted user', async () => {
  const calls = []
  const page = await renderUsersPage(async (path, options = {}) => {
    calls.push({ options, path })
    return path.includes('?query=') ? apiResponse(listedUsers()) : apiResponse()
  })

  try {
    await act(async () => buttonWithText(page.container, 'Unban account').click())
    await flush()
    assert.equal(calls[1].path, '/api/admin/users/8/banned')
    assert.equal(calls[1].options.body, JSON.stringify({ banned: false }))

    await act(async () => lastButtonWithText(page.container, 'Delete account').click())
    assert.match(page.container.textContent, /Delete Bob permanently/)
    const confirmButton = buttonWithText(page.container, 'Delete')
    assert.match(confirmButton.className, /text-white/)
    assert.doesNotMatch(confirmButton.className, /text-app-danger/)
    assert.equal(calls.filter(call => call.options.method === 'DELETE').length, 0)

    await act(async () => buttonWithText(page.container, 'Cancel').click())
    assert.equal(calls.filter(call => call.options.method === 'DELETE').length, 0)

    await act(async () => lastButtonWithText(page.container, 'Delete account').click())
    await act(async () => buttonWithText(page.container, 'Delete').click())
    await flush()

    assert.equal(calls.filter(call => call.options.method === 'DELETE').length, 1)
    assert.doesNotMatch(page.container.textContent, /@bob/)
  } finally {
    await page.unmount()
  }
})

test('failed delete keeps its row and dialog and shows the mapped account error', async () => {
  const page = await renderUsersPage(async (path, options = {}) => {
    if (options.method === 'DELETE') {
      return apiResponse(null, { code: 50001, ok: false, status: 500 })
    }
    return apiResponse(listedUsers())
  })

  try {
    await act(async () => lastButtonWithText(page.container, 'Delete account').click())
    await act(async () => buttonWithText(page.container, 'Delete').click())
    await flush()

    assert.match(page.container.textContent, /@bob/)
    assert.match(page.container.textContent, /Delete Bob permanently/)
    assert.match(page.container.textContent, /Account action failed/)
    assert.equal(buttonWithText(page.container, 'Delete').disabled, false)
  } finally {
    await page.unmount()
  }
})

test('offline core maps permanent delete to an explicit user-service error', async () => {
  const page = await renderUsersPage(async (path, options = {}) => {
    if (options.method === 'DELETE') {
      return apiResponse(null, { code: 50301, ok: false, status: 503 })
    }
    return apiResponse(listedUsers())
  })

  try {
    await act(async () => lastButtonWithText(page.container, 'Delete account').click())
    await act(async () => buttonWithText(page.container, 'Delete').click())
    await flush()

    assert.match(page.container.textContent, /User service is not running/)
  } finally {
    await page.unmount()
  }
})

test('server self-account rejection maps to the dedicated localized error', async () => {
  const page = await renderUsersPage(async (path, options = {}) => {
    if (options.method === 'PUT') {
      return apiResponse(null, { code: 40902, ok: false, status: 409 })
    }
    return apiResponse(listedUsers())
  }, 99)

  try {
    await act(async () => buttonWithText(page.container, 'Unban account').click())
    await flush()
    assert.match(page.container.textContent, /You cannot ban or delete your current account/)
  } finally {
    await page.unmount()
  }
})

test('account controls use strict self comparison and existing app color tokens', () => {
  assert.match(pageSource, /currentAdminId\s*===\s*user\.id/)
  assert.match(pageSource, /pages\.users\.banUser/)
  assert.match(pageSource, /pages\.users\.unbanUser/)
  assert.match(pageSource, /pages\.users\.deleteUser/)
  assert.match(pageSource, /pages\.users\.deleteConfirmTitle/)
  assert.match(pageSource, /text-app-danger/)
  assert.match(pageSource, /text-app-success/)
})

test('user row stacks on narrow pages and returns to two columns when space allows', () => {
  assert.match(pageSource, /className="admin-responsive-grid admin-responsive-grid--user-controls"/)
  assert.match(
    styleSource,
    /@container\s*\(min-width:\s*44rem\)[\s\S]*?\.admin-responsive-grid--user-controls\s*\{[^}]*grid-template-columns:\s*minmax\(220px,\s*0\.8fr\)\s+minmax\(320px,\s*1\.2fr\)/,
  )
})

test('overview and all locales describe current user management instead of future hookup', () => {
  assert.match(appSource, /subtitle={t\('pages\.users\.description'\)}/)
  assert.doesNotMatch(appSource, /pages\.users\.placeholder/)

  for (const locale of locales) {
    assert.equal(Object.hasOwn(locale.pages.users, 'placeholder'), false)
    assert.equal(Object.hasOwn(locale.pages.users, 'placeholderTitle'), false)
  }
})

test('last administrator failure uses a localized error', () => {
  assert.match(pageSource, /error\.code\s*===\s*40901/)
  assert.match(pageSource, /pages\.users\.lastAdminError/)
  assert.match(pageSource, /role="alert"/)
})

test('browser never sends a feature key for Codex access', () => {
  assert.doesNotMatch(pageSource, /featureKey|feature\.ai\.codex/i)
})

test('users consume platform offering metadata without exposing platform internals on rows', () => {
  assert.match(pageSource, /result\.runtimeAvailable/)
  assert.match(pageSource, /result\.offerings/)
  assert.match(pageSource, /user\.offeringAccess/)
  assert.match(pageSource, /updateUserOfferingAccess/)
  assert.match(pageSource, /pages\.users\.platformProvided/)
  assert.doesNotMatch(pageSource, /user\.connectionKey|user\.baseUrl|user\.apiKey|user\.account/)
})

test('runtime unavailable leaves users visible and disables platform access writes', async () => {
  const response = listedUsers()
  response.runtimeAvailable = false
  response.offerings = [{
    offeringKey: 'platform-model', source: 'PLATFORM', connectionKey: 'private-connection', kind: 'API',
    protocol: 'ANTHROPIC', cliKey: null, modelKey: 'claude-3-5-sonnet', defaultAccess: false,
  }]
  response.users = response.users.map(entry => ({
    ...entry, offeringAccess: { 'platform-model': false },
  }))
  const page = await renderUsersPage(async () => apiResponse(response))

  try {
    assert.match(page.container.textContent, /@amy/)
    assert.match(page.container.textContent, /runtime unavailable/i)
    await act(async () => buttonWithText(page.container, 'Manage').click())
    const toggle = page.container.querySelector('[aria-label*="claude-3-5-sonnet"]')
    const input = toggle.matches('input') ? toggle : toggle.querySelector('input')
    assert.equal(input.disabled, true)
    assert.doesNotMatch(page.container.textContent, /private-connection/)
  } finally {
    await page.unmount()
  }
})

test('online platform offering toggle sends a per-user override and updates the UI', async () => {
  const response = listedUsers()
  response.runtimeAvailable = true
  response.offerings = [{
    offeringKey: 'platform-model', source: 'PLATFORM', kind: 'API', protocol: 'ANTHROPIC', cliKey: null,
    modelKey: 'claude-3-5-sonnet', defaultAccess: false,
  }]
  response.users = response.users.map(entry => ({
    ...entry, offeringAccess: { 'platform-model': false },
  }))
  const calls = []
  const page = await renderUsersPage(async (path, options = {}) => {
    calls.push({ path, options })
    return apiResponse(path.includes('?query=') ? response : null)
  })

  try {
    await act(async () => buttonWithText(page.container, 'Manage').click())
    const toggle = page.container.querySelector('[aria-label*="claude-3-5-sonnet"]')
    const input = toggle.matches('input') ? toggle : toggle.querySelector('input')
    await act(async () => input.click())
    await flush()
    assert.equal(calls[1].path, '/api/admin/platform-ai/offerings/platform-model/access/users/7')
    assert.equal(calls[1].options.body, JSON.stringify({ enabled: true }))
    assert.equal(input.checked, true)
  } finally {
    await page.unmount()
  }
})

test('platform marker and access controls use source instead of kind or protocol', async () => {
  const response = listedUsers()
  response.runtimeAvailable = true
  response.offerings = [
    {
      offeringKey: 'lookalike-user-model', source: 'USER', kind: 'API',
      protocol: 'ANTHROPIC', cliKey: null, modelKey: 'must-not-render', defaultAccess: false,
    },
    {
      offeringKey: 'platform-model', source: 'PLATFORM', kind: 'CUSTOM',
      protocol: 'CUSTOM', cliKey: null, modelKey: 'platform-by-source', defaultAccess: false,
    },
  ]
  response.users = response.users.map(entry => ({
    ...entry,
    offeringAccess: { 'lookalike-user-model': false, 'platform-model': false },
  }))
  const page = await renderUsersPage(async () => apiResponse(response))

  try {
    await act(async () => buttonWithText(page.container, 'Manage').click())
    assert.match(page.container.textContent, /platform-by-source/)
    assert.match(page.container.textContent, /Platform provided/)
    assert.doesNotMatch(page.container.textContent, /must-not-render/)
  } finally {
    await page.unmount()
  }
})
