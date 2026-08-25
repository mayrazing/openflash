import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { after, test } from 'node:test'
import { JSDOM } from 'jsdom'
import { createInstance } from 'i18next'
import { I18nextProvider, initReactI18next } from 'react-i18next'
import { createServer } from 'vite'
import en from '../locales/en.json' with { type: 'json' }

const loginSource = await readFile(new URL('./AdminLogin.jsx', import.meta.url), 'utf8')
const usersSource = await readFile(new URL('../users/UsersPage.jsx', import.meta.url), 'utf8')
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://admin.test/login' })
Object.assign(globalThis, {
  document: dom.window.document,
  Event: dom.window.Event,
  HTMLElement: dom.window.HTMLElement,
  IS_REACT_ACT_ENVIRONMENT: true,
  Node: dom.window.Node,
  window: dom.window,
})
Object.defineProperty(globalThis, 'navigator', { configurable: true, value: dom.window.navigator })
globalThis.getComputedStyle = dom.window.getComputedStyle.bind(dom.window)

const [{ act, createElement }, { createRoot }, { App: KonstaApp }] = await Promise.all([
  import('react'),
  import('react-dom/client'),
  import('konsta/react'),
])
const vite = await createServer({ appType: 'custom', server: { middlewareMode: true } })
const { default: AdminLogin } = await vite.ssrLoadModule('/src/auth/AdminLogin.jsx')
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

test('formal anonymous login renders both credential fields', async () => {
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)

  try {
    await act(async () => {
      root.render(createElement(
        KonstaApp,
        { dark: false, theme: 'ios' },
        createElement(I18nextProvider, { i18n }, createElement(AdminLogin, {
          onAuthenticated() {},
        })),
      ))
      await Promise.resolve()
    })

    assert.ok(container.querySelector('input[autocomplete="username"]'))
    assert.ok(container.querySelector('input[autocomplete="current-password"]'))
  } finally {
    await act(async () => root.unmount())
    container.remove()
  }
})

test('all formal labeled inputs use the approved safe wrapper', () => {
  assert.match(loginSource, /import AppListInput from ['"]\.\.\/components\/AppListInput\.jsx['"]/)
  assert.match(usersSource, /import AppListInput from ['"]\.\.\/components\/AppListInput\.jsx['"]/)
  assert.doesNotMatch(loginSource, /\bListInput\b/)
  assert.doesNotMatch(usersSource, /\bListInput\b/)
})
