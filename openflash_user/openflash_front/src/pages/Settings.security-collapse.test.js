import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { createServer } from 'vite'

let browser
let server
let baseUrl

before(async () => {
  server = await createServer({
    root: new URL('../..', import.meta.url).pathname,
    logLevel: 'error',
    server: { host: '127.0.0.1', port: 43991, strictPort: true },
    plugins: [settingsHarnessPlugin()],
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

test('account security starts collapsed and expands on demand', async t => {
  const page = await browser.newPage()
  t.after(() => page.close())
  await page.goto(baseUrl)

  const currentPassword = page.locator('#settings-current-password')
  assert.equal(await currentPassword.isVisible(), false)

  await page.locator('summary').filter({ hasText: 'Account security' }).click()
  assert.equal(await currentPassword.isVisible(), true)
})

test('password inputs do not render outer hairlines', async t => {
  const page = await browser.newPage()
  t.after(() => page.close())
  await page.goto(baseUrl)

  await page.locator('summary').filter({ hasText: 'Account security' }).click()
  const hairlineContent = await page.locator('#settings-current-password').evaluate((input) => {
    const list = input.closest('.k-list')
    return {
      top: window.getComputedStyle(list, '::before').content,
      bottom: window.getComputedStyle(list, '::after').content,
    }
  })

  assert.deepEqual(hairlineContent, { top: 'none', bottom: 'none' })
})

test('each password input clears its own value', async t => {
  const page = await browser.newPage()
  t.after(() => page.close())
  await page.goto(baseUrl)
  await page.locator('summary').filter({ hasText: 'Account security' }).click()

  for (const inputId of [
    'settings-current-password',
    'settings-new-password',
    'settings-confirm-password',
  ]) {
    const input = page.locator(`#${inputId}`)
    await input.fill('password-value')
    await page.locator('.k-list-input').filter({ has: input }).locator('svg').click()
    assert.equal(await input.inputValue(), '')
  }
})

function settingsHarnessPlugin() {
  const entryId = '\0settings-security-test-entry'
  const databaseId = '\0settings-database-mock'
  const themeId = '\0settings-theme-mock'
  const soundId = '\0settings-sound-mock'
  const aiId = '\0settings-ai-mock'
  const pluginSlotId = '\0settings-plugin-slot-mock'

  return {
    name: 'settings-security-test-harness',
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
          <script type="module" src="/__settings-security-test-entry.jsx"></script>
        `)
      })
    },
    resolveId(source, importer) {
      if (source === '/__settings-security-test-entry.jsx') return entryId
      if (source === '../db/database' && importer?.endsWith('/src/pages/Settings.jsx')) return databaseId
      if (source === '../lib/theme' && importer?.endsWith('/src/pages/Settings.jsx')) return themeId
      if (source === '../lib/soundEngine' && importer?.endsWith('/src/pages/Settings.jsx')) return soundId
      if (source === '../ai/AiSettingsSection.jsx' && importer?.endsWith('/src/pages/Settings.jsx')) return aiId
      if (source === '../plugins/pluginSlot' && importer?.endsWith('/src/pages/Settings.jsx')) return pluginSlotId
      return null
    },
    load(id) {
      if (id === entryId) return `
        import React from 'react'
        import { createRoot } from 'react-dom/client'
        import { MemoryRouter } from 'react-router-dom'
        import i18next from 'i18next'
        import { I18nextProvider } from 'react-i18next'
        import '/src/index.css'
        import en from '/src/locales/en.json'
        import Settings from '/src/pages/Settings.jsx'
        const translations = i18next.createInstance()
        await translations.init({ lng: 'en', resources: { en: { translation: en } } })
        createRoot(document.getElementById('root')).render(
          React.createElement(I18nextProvider, { i18n: translations },
            React.createElement(MemoryRouter, null,
              React.createElement(Settings, {
                currentUser: { username: 'amy', nickname: 'Amy' },
                onLogout: async () => {},
                onPasswordChanged: () => {},
              }),
            ),
          ),
        )
      `
      if (id === databaseId) return `
        export const getSettings = async () => ({ theme: 'light', soundEnabled: true, language: 'en' })
        export const saveSettings = async value => value
        export const getLanguageOptions = async () => [{ value: 'en', label: 'English' }]
        export const changePassword = async () => null
      `
      if (id === themeId) return `export const useTheme = () => ({ theme: 'light', toggleTheme: () => {} })`
      if (id === soundId) return `
        export const setSoundEnabled = () => {}
        export const withGenericClick = handler => handler
      `
      if (id === aiId || id === pluginSlotId) return `export default function Empty() { return null }`
      return null
    },
  }
}
