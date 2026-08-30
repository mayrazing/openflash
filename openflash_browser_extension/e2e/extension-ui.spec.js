import { createServer } from 'node:http'
import { access, mkdtemp, mkdir, readFile, readdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium, expect, test as base } from '@playwright/test'
import { assertSupportedReloadOutcome, cleanupExtensionProfile } from './extensionFixture.js'

const extensionRoot = fileURLToPath(new URL('../', import.meta.url))
const extensionPath = resolve(extensionRoot, 'dist')
const screenshotDir = resolve(extensionRoot, 'test-results/browser-extension')
const requestLog = []
let mockServer
let mockOrigin
const longNickname = 'reader-with-a-production-length-account-name@example.com'

const labels = {
  'manualCard.title': 'Quick manual card',
  'manualCard.sideA': 'Side A',
  'manualCard.sideB': 'Side B',
  'manualCard.save': 'Save',
  'manualCard.saving': 'Saving...',
  'manualCard.cancel': 'Cancel',
  'manualCard.unsavedTitle': 'Unsaved content',
  'manualCard.unsavedConfirm': 'Close',
  'manualCard.unsavedBack': 'Keep editing',
  'manualCard.emptyContent': 'Fill in at least one side or paste an image',
}

function launchExtensionContext(userDataDir) {
  return chromium.launchPersistentContext(userDataDir, {
    channel: 'chromium',
    headless: true,
    args: [
      `--disable-extensions-except=${extensionPath}`,
      `--load-extension=${extensionPath}`,
    ],
  })
}

function watchRuntime(context, runtimeErrors) {
  const watchedPages = new WeakSet()
  const watchedWorkers = new WeakSet()
  const recordConsoleError = (message, source) => {
    if (message.type() === 'error') runtimeErrors.push(`console ${source}: ${message.text()}`)
  }
  const watchPage = (page) => {
    if (watchedPages.has(page)) return
    watchedPages.add(page)
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror ${page.url()}: ${error.message}`))
  }
  const watchServiceWorker = (worker) => {
    if (watchedWorkers.has(worker)) return
    watchedWorkers.add(worker)
    worker.on('console', (message) => recordConsoleError(message, worker.url()))
  }

  context.on('console', (message) => recordConsoleError(message, message.location().url || 'browser context'))
  context.on('page', watchPage)
  context.on('serviceworker', watchServiceWorker)
  context.pages().forEach(watchPage)
  context.serviceWorkers().forEach(watchServiceWorker)
}

async function initialServiceWorker(context) {
  let [serviceWorker] = context.serviceWorkers()
  serviceWorker ||= await context.waitForEvent('serviceworker', { timeout: 10_000 })
  return serviceWorker
}

async function smokeServiceWorker(context, serviceWorker) {
  const extensionId = new URL(serviceWorker.url()).host
  const manifest = await serviceWorker.evaluate(async (serviceUrl) => {
    await chrome.storage.local.set({ serviceUrl })
    return {
      manifestVersion: chrome.runtime.getManifest().manifest_version,
      runtimeId: chrome.runtime.id,
    }
  }, mockOrigin)
  expect(manifest).toEqual({ manifestVersion: 3, runtimeId: extensionId })
  const smokePage = await context.newPage()
  await smokePage.goto(`chrome-extension://${extensionId}/popup.html`)
  const refreshResponse = await smokePage.evaluate(
    () => chrome.runtime.sendMessage({ type: 'OPENFLASH_REFRESH_MENUS' }),
  )
  expect(refreshResponse).toEqual({ ok: true })
  await smokePage.close()
  return extensionId
}

async function reloadAndWatchServiceWorker(context, serviceWorker) {
  const extensionId = new URL(serviceWorker.url()).host
  const contextClosedSignal = Symbol('context closed')
  const contextClosed = new Promise((resolveClosed) => {
    context.once('close', () => resolveClosed(contextClosedSignal))
  })
  const initialWorkerClosed = new Promise((resolveClosed) => {
    serviceWorker.once('close', () => resolveClosed({ kind: 'worker-closed' }))
  })
  const futureServiceWorker = new Promise((resolveWorker) => {
    const onServiceWorker = (worker) => {
      if (worker === serviceWorker) return
      context.off('serviceworker', onServiceWorker)
      resolveWorker({ kind: 'service-worker', serviceWorker: worker })
    }
    context.on('serviceworker', onServiceWorker)
  })

  await serviceWorker.evaluate(() => chrome.runtime.reload())
  const shutdown = await withTimeout(
    Promise.race([contextClosed, initialWorkerClosed]),
    'extension reload did not close the initial worker',
  )
  if (shutdown === contextClosedSignal) throw new Error('extension reload closed the browser context unexpectedly')

  let wakePage
  try {
    wakePage = await context.newPage()
    await wakePage.goto(`chrome-extension://${extensionId}/popup.html`)
    await wakePage.evaluate(() => chrome.runtime.sendMessage({ type: 'OPENFLASH_E2E_WAKE' }).catch(() => {}))
  } catch (error) {
    if (/Target page, context or browser has been closed/.test(error.message)) {
      throw new Error('extension reload closed the browser context unexpectedly', { cause: error })
    }
    if (/net::ERR_BLOCKED_BY_CLIENT/.test(error.message)) {
      return { kind: 'extension-unloaded' }
    }
    throw error
  }

  const outcome = await withTimeout(
    Promise.race([contextClosed, futureServiceWorker]),
    'extension reload did not create a monitored service worker',
  )
  if (outcome === contextClosedSignal) throw new Error('extension reload closed the browser context unexpectedly')
  await wakePage.close()
  return outcome
}

async function withTimeout(promise, message) {
  let timeoutId
  const timeout = new Promise((_, reject) => {
    timeoutId = setTimeout(() => reject(new Error(message)), 10_000)
  })
  try {
    return await Promise.race([promise, timeout])
  } finally {
    clearTimeout(timeoutId)
  }
}

const test = base.extend({
  extension: async ({}, use) => {
    const reloadProfile = await mkdtemp(join(tmpdir(), 'openflash-extension-reload-e2e-'))
    let reloadContext = null

    try {
      const reloadErrors = []
      reloadContext = await launchExtensionContext(reloadProfile)
      watchRuntime(reloadContext, reloadErrors)
      const workerBeforeReload = await initialServiceWorker(reloadContext)
      const browserVersion = reloadContext.browser().version()
      const reloadOutcome = assertSupportedReloadOutcome(
        await reloadAndWatchServiceWorker(reloadContext, workerBeforeReload),
        browserVersion,
      )
      if (reloadOutcome.kind === 'service-worker') {
        await smokeServiceWorker(reloadContext, reloadOutcome.serviceWorker)
      }
      expect(reloadErrors, 'extension reload console errors must not be silent').toEqual([])
      console.log('[e2e-evidence] reload', { browserVersion, kind: reloadOutcome.kind, runtimeErrors: reloadErrors })
    } finally {
      await cleanupExtensionProfile(reloadContext, reloadProfile)
    }

    const userDataDir = await mkdtemp(join(tmpdir(), 'openflash-extension-e2e-'))
    let context = null
    try {
      const runtimeErrors = []
      context = await launchExtensionContext(userDataDir)
      watchRuntime(context, runtimeErrors)
      const serviceWorker = await initialServiceWorker(context)
      const extensionId = await smokeServiceWorker(context, serviceWorker)
      expect(runtimeErrors, 'extension startup console errors must not be silent').toEqual([])

      await use({ context, extensionId, serviceWorker })
      expect(runtimeErrors, 'browser console/page errors must not be silent').toEqual([])
    } finally {
      await cleanupExtensionProfile(context, userDataDir)
    }
  },
})

test.describe.configure({ mode: 'serial' })

test.beforeAll(async () => {
  await mkdir(screenshotDir, { recursive: true })
  mockServer = createServer((request, response) => {
    const url = new URL(request.url || '/', 'http://127.0.0.1')
    requestLog.push({ method: request.method, path: url.pathname })
    response.setHeader('Access-Control-Allow-Headers', 'Content-Type')
    response.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,DELETE,OPTIONS')
    response.setHeader('Access-Control-Allow-Origin', '*')

    if (request.method === 'OPTIONS') {
      response.writeHead(204).end()
      return
    }
    if (url.pathname === '/test-page') {
      response.setHeader('Content-Type', 'text/html; charset=utf-8')
      response.end('<!doctype html><html><body><h1>Ordinary test page</h1><p>Extension host page.</p></body></html>')
      return
    }
    if (url.pathname === '/api/decks' && request.method === 'GET') {
      respondJson(response, { code: 200, data: [] })
      return
    }
    if (url.pathname.startsWith('/failure/api/')) {
      respondJson(response, { code: 50001, message: 'login failed' })
      return
    }
    if (url.pathname === '/success/api/auth/me') {
      respondJson(response, { code: 200, data: { id: 1, nickname: longNickname, username: 'e2e-user' } })
      return
    }
    if (url.pathname === '/success/api/settings') {
      respondJson(response, { code: 200, data: { language: 'zh' } })
      return
    }
    if (url.pathname === '/success/api/decks' && request.method === 'GET') {
      respondJson(response, {
        code: 200,
        data: [
          { id: 7, name: '这是一个很长很长的中文卡包名称用于验证省略' },
          { id: 8, name: 'NewEastVocabulary' },
        ],
      })
      return
    }
    if (url.pathname === '/success/api/decks/7/ai-settings') {
      respondJson(response, {
        code: 200,
        data: { aiCompletionEnabled: false, aiCompletionPrompt: null },
      })
      return
    }
    if (url.pathname === '/success/api/decks' && request.method === 'POST') {
      respondJson(response, { code: 200, data: { id: 8, name: 'Created deck' } })
      return
    }
    respondJson(response, { code: 200, data: null })
  })
  await new Promise((resolveListen) => mockServer.listen(0, '127.0.0.1', resolveListen))
  const address = mockServer.address()
  mockOrigin = `http://127.0.0.1:${address.port}`
})

test.afterAll(async () => {
  await new Promise((resolveClose, rejectClose) => {
    mockServer.close((error) => error ? rejectClose(error) : resolveClose())
  })
})

test('Chinese popup keeps the approved compact layout, semantic themes, and visible focus', async ({ extension }) => {
  const { context, extensionId, serviceWorker } = extension
  await closePages(context)
  await setStorage(serviceWorker, { serviceUrl: `${mockOrigin}/failure` })
  const page = await context.newPage()
  await page.setViewportSize({ width: 380, height: 720 })
  await page.emulateMedia({ colorScheme: 'light', contrast: 'no-preference' })
  await page.goto(`chrome-extension://${extensionId}/popup.html`)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.k-app')).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('API error 50001')
  await page.content()

  await setStorage(serviceWorker, {
    serviceUrl: `${mockOrigin}/success`,
    lastImportStatus: null,
  })
  await page.reload()
  await page.waitForLoadState('networkidle')
  await expect(page.locator('#newDeckName')).toBeVisible()
  const createRequestsBefore = countRequests('POST', '/success/api/decks')
  await page.getByRole('button', { name: '新建', exact: true }).click()
  await page.waitForTimeout(100)
  expect(countRequests('POST', '/success/api/decks')).toBe(createRequestsBefore)
  await expect(page.locator('[aria-current="true"]')).toContainText('已选中')
  await expect(page.getByRole('alert')).toHaveCount(0)

  const dimensions = await page.evaluate(() => ({
    appWidth: document.querySelector('.k-app')?.getBoundingClientRect().width,
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(dimensions).toEqual({ appWidth: 380, clientWidth: 380, scrollWidth: 380 })
  const navbarLayout = await page.evaluate((nickname) => {
    const navbar = document.querySelector('[data-testid="popup-header"]')
    const identity = navbar.querySelector('[data-testid="popup-identity"]')
    const title = navbar.querySelector('h1')
    const account = navbar.querySelector(`span[title="${nickname}"]`)
    const logout = Array.from(navbar.querySelectorAll('button')).find((node) => node.textContent === '退出登录')
    const identityBox = identity.getBoundingClientRect()
    const titleBox = title.getBoundingClientRect()
    const accountBox = account.getBoundingClientRect()
    const logoutBox = logout.getBoundingClientRect()
    return {
      account: {
        clipped: account.scrollWidth > account.clientWidth,
        left: accountBox.left,
        right: accountBox.right,
        top: accountBox.top,
      },
      horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      identity: { left: identityBox.left, right: identityBox.right },
      logout: { left: logoutBox.left, right: logoutBox.right },
      title: {
        bottom: titleBox.bottom,
        clipped: title.scrollWidth > title.clientWidth,
        left: titleBox.left,
        right: titleBox.right,
      },
      viewportWidth: innerWidth,
    }
  }, longNickname)
  expect(navbarLayout.title.bottom).toBeLessThanOrEqual(navbarLayout.account.top)
  expect(navbarLayout.identity.right).toBeLessThanOrEqual(navbarLayout.logout.left)
  for (const box of [navbarLayout.title, navbarLayout.account, navbarLayout.identity, navbarLayout.logout]) {
    expect(box.left).toBeGreaterThanOrEqual(0)
    expect(box.right).toBeLessThanOrEqual(navbarLayout.viewportWidth)
  }
  expect(navbarLayout.title.clipped).toBe(false)
  expect(navbarLayout.account.clipped).toBe(true)
  expect(navbarLayout.horizontalScroll).toBe(false)
  await expect(page.locator(`span[title="${longNickname}"]`)).toHaveAttribute('aria-label', longNickname)
  const deckLayout = await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll('[data-testid^="deck-row-"]'))
    return rows.map((row) => {
      const main = row.querySelector('[data-testid^="deck-main-"]')
      const actions = row.querySelector('[data-testid^="deck-actions-"]')
      const deleteButton = Array.from(actions.querySelectorAll('button')).find((button) => button.textContent === '删除')
      const rowBox = row.getBoundingClientRect()
      const mainBox = main.getBoundingClientRect()
      const actionsBox = actions.getBoundingClientRect()
      const buttons = Array.from(actions.querySelectorAll('button'))
      return {
        actionsInside: actionsBox.left >= rowBox.left && actionsBox.right <= rowBox.right,
        buttonsSingleLine: buttons.every((button) => button.scrollHeight <= button.clientHeight),
        deleteBackground: getComputedStyle(deleteButton).backgroundColor,
        stacked: actionsBox.top >= mainBox.bottom,
      }
    })
  })
  expect(deckLayout).toHaveLength(2)
  for (const row of deckLayout) {
    expect(row).toMatchObject({
      actionsInside: true,
      buttonsSingleLine: true,
      deleteBackground: 'rgba(0, 0, 0, 0)',
      stacked: true,
    })
  }
  const light = await themeColors(page, '.k-app', '#serviceUrl')
  expect(light).toMatchObject({ background: 'rgb(242, 242, 247)', label: 'rgb(0, 0, 0)' })
  expect(light.background).toBe(light.backgroundToken)
  expect(light.label).toBe(light.labelToken)
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'popup-light.png') })

  const focus = await focusFirstButtonWithKeyboard(page)
  expect(focus.tagName).toBe('BUTTON')
  expect(hasVisibleFocus(focus)).toBe(true)

  await page.emulateMedia({ colorScheme: 'dark', contrast: 'no-preference' })
  await expect(page.locator('html')).toHaveClass(/dark/)
  const dark = await themeColors(page, '.k-app', '#serviceUrl')
  expect(dark).toMatchObject({ background: 'rgb(28, 28, 30)', label: 'rgb(255, 255, 255)' })
  expect(dark.background).toBe(dark.backgroundToken)
  expect(dark.label).toBe(dark.labelToken)
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'popup-dark.png') })

  await page.emulateMedia({ colorScheme: 'dark', contrast: 'more' })
  const highContrast = await themeColors(page, '.k-app', '#serviceUrl')
  expect(highContrast).toMatchObject({ background: 'rgb(36, 36, 38)', label: 'rgb(255, 255, 255)' })
  expect(highContrast.background).toBe(highContrast.backgroundToken)
  console.log('[e2e-evidence] popup', { dimensions, navbarLayout, light, dark, highContrast, focus })
})

test('deck deletion waits for an explicit confirmation before it reaches the API', async ({ extension }) => {
  const { context, extensionId, serviceWorker } = extension
  await closePages(context)
  await setStorage(serviceWorker, { serviceUrl: `${mockOrigin}/success` })
  const page = await context.newPage()
  await page.setViewportSize({ width: 380, height: 720 })
  await page.goto(`chrome-extension://${extensionId}/popup.html`)
  await page.waitForLoadState('networkidle')

  const deckRow = page.locator('[data-testid="deck-row-8"]')
  await expect(deckRow).toBeVisible()
  const deleteRequestsBefore = countRequests('DELETE', '/success/api/decks/8')

  await deckRow.getByRole('button', { name: '删除', exact: true }).click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('NewEastVocabulary')
  expect(countRequests('DELETE', '/success/api/decks/8')).toBe(deleteRequestsBefore)
  await page.screenshot({ path: resolve(screenshotDir, 'popup-delete-confirm.png') })

  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await page.waitForTimeout(100)
  expect(countRequests('DELETE', '/success/api/decks/8')).toBe(deleteRequestsBefore)

  await deckRow.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.getByRole('alertdialog')).toHaveCount(0)
  await expect
    .poll(() => countRequests('DELETE', '/success/api/decks/8'))
    .toBe(deleteRequestsBefore + 1)
})

test('shortcut page keeps 440px content, both commands and working settings entry', async ({ extension }) => {
  const { context, extensionId, serviceWorker } = extension
  await closePages(context)
  const page = await context.newPage()
  await page.setViewportSize({ width: 800, height: 650 })
  await page.emulateMedia({ colorScheme: 'light', contrast: 'no-preference' })
  await page.goto(`chrome-extension://${extensionId}/shortcutSetup.html`)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.k-app')).toBeVisible()
  await expect(page.getByText('Alt+Shift+D', { exact: true })).toBeVisible()
  await expect(page.getByText('Alt+Shift+A', { exact: true })).toBeVisible()
  const settingsButton = page.getByRole('button', { name: 'Open shortcut settings' })
  await expect(settingsButton).toBeVisible()
  await page.content()

  const mainWidth = await page.locator('#app .k-app main').evaluate((node) => node.getBoundingClientRect().width)
  expect(mainWidth).toBe(440)
  const light = await themeColors(page, '.k-app', 'main p')
  expect(light.background).toBe('rgb(242, 242, 247)')
  expect(light.background).toBe(light.backgroundToken)
  expect(light.label).toBe(light.secondaryLabelToken)
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'shortcut-light.png') })

  await page.emulateMedia({ colorScheme: 'dark', contrast: 'no-preference' })
  await expect(page.locator('html')).toHaveClass(/dark/)
  const dark = await themeColors(page, '.k-app', 'main p')
  expect(dark.background).toBe('rgb(28, 28, 30)')
  expect(dark.background).toBe(dark.backgroundToken)
  expect(dark.label).toBe(dark.secondaryLabelToken)

  await page.emulateMedia({ colorScheme: 'light', contrast: 'more' })
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  const highContrast = await themeColors(page, '.k-app', 'main p')
  expect(highContrast.background).toBe('rgb(235, 235, 240)')
  expect(highContrast.background).toBe(highContrast.backgroundToken)

  const pageCountBefore = context.pages().length
  await settingsButton.click()
  await expect.poll(() => context.pages().length).toBeGreaterThan(pageCountBefore)
  const tabUrls = context.pages().map((tab) => tab.url())
  expect(tabUrls.some((url) => url.startsWith('chrome://extensions'))).toBe(true)
  console.log('[e2e-evidence] shortcut', { mainWidth, light, dark, highContrast, tabUrls })
})

test('manual card is isolated, clamped, theme-reactive and unmounted on close', async ({ extension }) => {
  const { context, serviceWorker } = extension
  await closePages(context)
  await setStorage(serviceWorker, { manualCardPosition: { left: 9999, top: -9999 } })
  const page = await context.newPage()
  await page.setViewportSize({ width: 900, height: 700 })
  await page.emulateMedia({ colorScheme: 'light', contrast: 'no-preference' })
  await page.goto(`${mockOrigin}/test-page`)
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('heading', { name: 'Ordinary test page' })).toBeVisible()
  expect(await shadowHostCount(page, '[data-side="a"]')).toBe(0)
  await page.evaluate(() => {
    window.__openflashBodyKeydowns = 0
    document.body.addEventListener('keydown', () => { window.__openflashBodyKeydowns += 1 })
  })

  await sendToPage(serviceWorker, page, {
    type: 'OPENFLASH_OPEN_MANUAL_CARD',
    deckId: '7',
    baseUrl: `${mockOrigin}/success`,
    labels,
  })
  const sideA = page.locator('[data-side="a"]')
  await expect(sideA).toBeVisible()
  await expect(page.locator('.k-card')).toBeVisible()
  expect(await page.locator('.k-button').count()).toBeGreaterThanOrEqual(2)
  expect(await shadowHostCount(page, '[data-side="a"]')).toBe(1)
  expect(await page.evaluate(() => document.activeElement?.shadowRoot?.activeElement?.dataset?.side)).toBe('a')

  const position = await manualHostPosition(page)
  expect(position.left).toBeGreaterThanOrEqual(16)
  expect(position.top).toBeGreaterThanOrEqual(16)
  expect(position.right).toBeLessThanOrEqual(900)
  expect(position.bottom).toBeLessThanOrEqual(700)
  await sendToPage(serviceWorker, page, {
    type: 'OPENFLASH_OPEN_MANUAL_CARD',
    deckId: '7',
    baseUrl: `${mockOrigin}/success`,
    labels,
  })
  expect(await shadowHostCount(page, '[data-side="a"]')).toBe(1)

  await sideA.press('x')
  expect(await page.evaluate(() => window.__openflashBodyKeydowns)).toBe(0)
  await page.emulateMedia({ colorScheme: 'dark', contrast: 'no-preference' })
  await expect.poll(() => page.locator('.openflash-konsta-root').evaluate((node) => node.classList.contains('dark'))).toBe(true)
  const dark = await themeColors(page, '.k-card', '[data-side="a"]')
  expect(dark.background).toBe('rgb(44, 44, 46)')
  expect(dark.background).toBe(dark.surfaceToken)
  expect(dark.label).toBe(dark.labelToken)
  const manualLayout = await page.evaluate(() => {
    const host = Array.from(document.body.children).find((node) => node.shadowRoot?.querySelector('[data-side="a"]'))
    const root = host.shadowRoot.querySelector('.openflash-konsta-root')
    const card = host.shadowRoot.querySelector('.k-card')
    const hostBox = host.getBoundingClientRect()
    const cardBox = card.getBoundingClientRect()
    return {
      cardBox: { bottom: cardBox.bottom, left: cardBox.left, right: cardBox.right, top: cardBox.top, width: cardBox.width },
      hostBackground: getComputedStyle(host).backgroundColor,
      hostBox: { bottom: hostBox.bottom, left: hostBox.left, right: hostBox.right, top: hostBox.top, width: hostBox.width },
      rootBackground: getComputedStyle(root).backgroundColor,
    }
  })
  expect(manualLayout.cardBox.left).toBeCloseTo(manualLayout.hostBox.left, 0)
  expect(manualLayout.cardBox.top).toBeCloseTo(manualLayout.hostBox.top, 0)
  expect(manualLayout.cardBox.right).toBeCloseTo(manualLayout.hostBox.right, 0)
  expect(manualLayout.cardBox.bottom).toBeCloseTo(manualLayout.hostBox.bottom, 0)
  expect(manualLayout.cardBox.width).toBeCloseTo(manualLayout.hostBox.width, 0)
  expect(manualLayout.hostBackground).toBe('rgba(0, 0, 0, 0)')
  expect(manualLayout.rootBackground).toBe('rgba(0, 0, 0, 0)')
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'manual-card-dark.png') })

  await page.emulateMedia({ colorScheme: 'dark', contrast: 'more' })
  const highContrast = await themeColors(page, '.k-card', '[data-side="a"]')
  expect(highContrast.background).toBe('rgb(54, 54, 56)')
  expect(highContrast.background).toBe(highContrast.surfaceToken)
  await page.emulateMedia({ colorScheme: 'dark', contrast: 'no-preference' })

  await sideA.press('Escape')
  await expect(page.getByText('Unsaved content', { exact: true })).toBeVisible()
  await page.evaluate(() => {
    const host = Array.from(document.body.children).find((node) => node.shadowRoot?.querySelector('[data-side], [data-role="confirm-close"]'))
    window.__oldManualHost = host
    window.__oldManualRoot = host?.shadowRoot?.querySelector('.openflash-konsta-root')
    window.__oldManualMount = host?.shadowRoot?.querySelector('.openflash-konsta-root')
  })
  await page.locator('[data-role="confirm-close"]').click()
  expect(await shadowHostCount(page, '[data-side="a"], [data-role="confirm-close"]')).toBe(0)
  expect(await page.evaluate(() => ({
    connected: window.__oldManualHost?.isConnected,
    mountedChildren: window.__oldManualMount?.childElementCount,
    wasDark: window.__oldManualRoot?.classList.contains('dark'),
  }))).toEqual({ connected: false, mountedChildren: 0, wasDark: true })
  await page.emulateMedia({ colorScheme: 'light', contrast: 'no-preference' })
  await page.waitForTimeout(50)
  expect(await page.evaluate(() => window.__oldManualRoot?.classList.contains('dark'))).toBe(true)

  await page.evaluate(() => window.getSelection()?.removeAllRanges())
  const createRequestsBefore = countRequests('POST', '/success/api/browser-import/decks/7/cards')
  await sendToPage(serviceWorker, page, {
    type: 'OPENFLASH_OPEN_MANUAL_CARD',
    deckId: '7',
    baseUrl: `${mockOrigin}/success`,
    labels,
  })
  await page.locator('[data-role="save"]').click()
  await expect(page.getByRole('alert')).toContainText(labels['manualCard.emptyContent'])
  expect(countRequests('POST', '/success/api/browser-import/decks/7/cards')).toBe(createRequestsBefore)
  console.log('[e2e-evidence] manual-card', { position, dark, highContrast, manualLayout })
})

test('page Toast uses final Apple fill/on-color and resets its timer', async ({ extension }) => {
  const { context, serviceWorker } = extension
  await closePages(context)
  const page = await context.newPage()
  await page.setViewportSize({ width: 900, height: 700 })
  await page.emulateMedia({ colorScheme: 'dark', contrast: 'no-preference' })
  await page.goto(`${mockOrigin}/test-page`)
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('heading', { name: 'Ordinary test page' })).toBeVisible()
  expect(await shadowHostCount(page, '.k-toast')).toBe(0)

  const evidence = {}
  await showNotification(serviceWorker, page, 'Saved successfully', 'success')
  evidence.success = await expectToastColors(page, '--app-success-fill', '--app-on-success')
  expect(evidence.success).toMatchObject({ background: 'rgb(48, 209, 88)', color: 'rgb(255, 255, 255)' })
  evidence.layout = await toastLayout(page)
  expect(evidence.layout.position).toBe('fixed')
  expect(evidence.layout.justifyContent).toBe('flex-end')
  expect(evidence.layout.bottom).toBe('16px')
  expect(evidence.layout.hostBox).toEqual({ height: 700, width: 900, x: 0, y: 0 })
  expect(evidence.layout.innerBox.x + evidence.layout.innerBox.width).toBeCloseTo(884, 4)
  expect(evidence.layout.innerBox.y + evidence.layout.innerBox.height).toBeCloseTo(684, 4)
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'toast-success-dark.png') })
  await page.waitForTimeout(1000)

  await showNotification(serviceWorker, page, 'Saved with warning', 'warning')
  evidence.warning = await expectToastColors(page, '--app-warning-fill', '--app-on-warning')
  expect(evidence.warning).toMatchObject({ background: 'rgb(255, 146, 48)', color: 'rgb(255, 255, 255)' })
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'toast-warning-dark.png') })
  await page.waitForTimeout(1500)
  await expect(page.locator('.k-toast')).toBeVisible()

  await showNotification(serviceWorker, page, 'Save failed', 'error')
  evidence.error = await expectToastColors(page, '--app-danger-fill', '--app-on-danger')
  expect(evidence.error).toMatchObject({ background: 'rgb(255, 66, 69)', color: 'rgb(255, 255, 255)' })
  await page.screenshot({ fullPage: true, path: resolve(screenshotDir, 'toast-error-dark.png') })
  expect(await shadowHostCount(page, '.k-toast')).toBe(1)

  await page.emulateMedia({ colorScheme: 'dark', contrast: 'more' })
  await showNotification(serviceWorker, page, 'High contrast success', 'success')
  evidence.highContrastSuccess = await expectToastColors(page, '--app-success-fill', '--app-on-success')
  expect(evidence.highContrastSuccess.background).toBe('rgb(74, 217, 104)')
  console.log('[e2e-evidence] toast', evidence)
})

test('built package is self-contained and every Manifest V3 entry exists', async ({ extension }) => {
  const manifest = JSON.parse(await readFile(resolve(extensionPath, 'manifest.json'), 'utf8'))
  expect(manifest.manifest_version).toBe(3)
  const entries = [
    manifest.action.default_popup,
    manifest.background.service_worker,
    ...manifest.content_scripts.flatMap((entry) => entry.js),
    'shortcutSetup.html',
  ]
  await Promise.all(entries.map((entry) => access(resolve(extensionPath, entry))))

  const files = await listFiles(extensionPath)
  const html = (await Promise.all(files.filter((file) => extname(file) === '.html').map((file) => readFile(file, 'utf8')))).join('\n')
  const css = (await Promise.all(files.filter((file) => extname(file) === '.css').map((file) => readFile(file, 'utf8')))).join('\n')
  expect(html).not.toMatch(/<script\b[^>]*\bsrc=["']https?:\/\//i)
  expect(html).not.toMatch(/<link\b[^>]*\bhref=["']https?:\/\/[^"']+\.(?:css|woff2?|ttf|otf)/i)
  expect(css).not.toMatch(/@import\s+(?:url\()?['"]?https?:\/\//i)
  expect(css).not.toMatch(/@font-face[^}]*url\(['"]?https?:\/\//is)
  expect(extension.serviceWorker.url()).toBe(`chrome-extension://${extension.extensionId}/assets/background.js`)
  expect(extension.extensionId).toMatch(/^[a-p]{32}$/)
  console.log('[e2e-evidence] chromium', extension.context.browser()?.version(), { entries })
})

function respondJson(response, payload) {
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.end(JSON.stringify(payload))
}

async function closePages(context) {
  await Promise.all(context.pages().map((page) => page.close()))
}

function countRequests(method, path) {
  return requestLog.filter((request) => request.method === method && request.path === path).length
}

async function setStorage(serviceWorker, values) {
  await serviceWorker.evaluate((nextValues) => chrome.storage.local.set(nextValues), values)
}

async function sendToPage(serviceWorker, page, message) {
  await page.bringToFront()
  return serviceWorker.evaluate(async ({ pageUrl, payload }) => {
    const tabs = await chrome.tabs.query({})
    const tab = tabs.find((candidate) => candidate.url === pageUrl)
    if (!tab?.id) throw new Error(`Could not find tab for ${pageUrl}`)
    return chrome.tabs.sendMessage(tab.id, payload)
  }, { pageUrl: page.url(), payload: message })
}

async function themeColors(page, backgroundSelector, labelSelector) {
  const backgroundNode = page.locator(backgroundSelector)
  const labelNode = page.locator(labelSelector)
  const tokens = await backgroundNode.evaluate((node) => {
    const rootNode = node.getRootNode()
    const scope = rootNode instanceof ShadowRoot
      ? rootNode.querySelector('.openflash-konsta-root')
      : document.documentElement
    const resolveColor = (property) => {
      const probe = document.createElement('span')
      probe.style.color = `var(${property})`
      scope.appendChild(probe)
      const value = getComputedStyle(probe).color
      probe.remove()
      return value
    }
    return {
      backgroundToken: resolveColor('--app-background'),
      surfaceToken: resolveColor('--app-surface-primary'),
      labelToken: resolveColor('--app-label-primary'),
      secondaryLabelToken: resolveColor('--app-label-secondary'),
    }
  })
  return {
    background: await backgroundNode.evaluate((node) => getComputedStyle(node).backgroundColor),
    label: await labelNode.evaluate((node) => getComputedStyle(node).color),
    ...tokens,
  }
}

async function focusFirstButtonWithKeyboard(page) {
  for (let index = 0; index < 12; index += 1) {
    await page.keyboard.press('Tab')
    const focus = await page.evaluate(() => {
      const active = document.activeElement
      const style = getComputedStyle(active)
      return {
        boxShadow: style.boxShadow,
        outlineColor: style.outlineColor,
        outlineStyle: style.outlineStyle,
        outlineWidth: style.outlineWidth,
        tagName: active?.tagName,
      }
    })
    if (focus.tagName === 'BUTTON') return focus
  }
  return { tagName: document.activeElement?.tagName }
}

function hasVisibleFocus(focus) {
  const outlined = focus.outlineStyle !== 'none' && Number.parseFloat(focus.outlineWidth) > 0
  return outlined || (focus.boxShadow && focus.boxShadow !== 'none')
}

async function shadowHostCount(page, innerSelector) {
  return page.evaluate((selector) => Array.from(document.body.children)
    .filter((node) => node.shadowRoot?.querySelector(selector)).length, innerSelector)
}

async function manualHostPosition(page) {
  return page.evaluate(() => {
    const host = Array.from(document.body.children).find((node) => node.shadowRoot?.querySelector('[data-side="a"]'))
    const bounds = host.getBoundingClientRect()
    return { left: bounds.left, top: bounds.top, right: bounds.right, bottom: bounds.bottom }
  })
}

async function showNotification(serviceWorker, page, message, level) {
  await sendToPage(serviceWorker, page, { type: 'OPENFLASH_SHOW_NOTIFICATION', message, level })
  await expect(page.locator('.k-toast')).toContainText(message)
  await expect.poll(() => page.locator('.k-toast').evaluate((node) => getComputedStyle(node).opacity)).toBe('1')
}

async function expectToastColors(page, backgroundToken, onColorToken) {
  const glass = page.locator('.k-glass')
  const tokens = await glass.evaluate((node, properties) => {
    const root = node.getRootNode().querySelector('.openflash-konsta-root')
    const resolveColor = (property) => {
      const probe = document.createElement('span')
      probe.style.color = `var(${property})`
      root.appendChild(probe)
      const value = getComputedStyle(probe).color
      probe.remove()
      return value
    }
    return {
      backgroundToken: resolveColor(properties.backgroundToken),
      colorToken: resolveColor(properties.onColorToken),
    }
  }, { backgroundToken, onColorToken })
  await expect.poll(() => glass.evaluate((node) => ({
    background: getComputedStyle(node).backgroundColor,
    color: getComputedStyle(node).color,
  }))).toEqual({ background: tokens.backgroundToken, color: tokens.colorToken })
  return {
    ...await glass.evaluate((node) => ({
      background: getComputedStyle(node).backgroundColor,
      color: getComputedStyle(node).color,
    })),
    ...tokens,
  }
}

async function toastLayout(page) {
  return page.locator('.k-toast').evaluate((toast) => {
    const host = toast.getRootNode().host
    const inner = toast.querySelector('.k-glass')
    const toastBox = toast.getBoundingClientRect()
    const innerBox = inner.getBoundingClientRect()
    const hostBox = host.getBoundingClientRect()
    const style = getComputedStyle(toast)
    return {
      bottom: style.bottom,
      display: style.display,
      hostBox: { height: hostBox.height, width: hostBox.width, x: hostBox.x, y: hostBox.y },
      innerBox: { height: innerBox.height, width: innerBox.width, x: innerBox.x, y: innerBox.y },
      justifyContent: style.justifyContent,
      position: style.position,
      toastBox: { height: toastBox.height, width: toastBox.width, x: toastBox.x, y: toastBox.y },
      viewport: { height: innerHeight, width: innerWidth },
    }
  })
}

async function listFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = await Promise.all(entries.map((entry) => {
    const path = resolve(directory, entry.name)
    return entry.isDirectory() ? listFiles(path) : [path]
  }))
  return files.flat()
}
