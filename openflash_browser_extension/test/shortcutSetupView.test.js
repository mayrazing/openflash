import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test, { after } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

const server = await createServer({
  root: new URL('..', import.meta.url).pathname,
  configFile: false,
  appType: 'custom',
  esbuild: { jsx: 'automatic', jsxImportSource: 'react' },
  optimizeDeps: { noDiscovery: true },
  server: { middlewareMode: true, hmr: false, ws: false },
})

after(() => server.close())

const shortcutModule = await server.ssrLoadModule('/src/shortcut/ShortcutSetupApp.jsx')
const { default: ShortcutSetupApp, openShortcutSettings } = shortcutModule
const source = readFileSync(new URL('../src/shortcut/ShortcutSetupApp.jsx', import.meta.url), 'utf8')

test('shortcut setup renders Konsta UI guide with both default command shortcuts', () => {
  const html = renderToStaticMarkup(createElement(ShortcutSetupApp, {
    chromeApi: { tabs: { create() {} } },
    navigatorApi: { language: 'en-US' },
  }))

  assert.match(html, /Set OpenFlash shortcuts/)
  assert.match(html, /Import selection directly/)
  assert.match(html, /Quick manual card/)
  assert.match(html, /<kbd[^>]*>Alt\+Shift\+D<\/kbd>/)
  assert.match(html, /<kbd[^>]*>Alt\+Shift\+A<\/kbd>/)
  assert.match(html, /<button[^>]*>Open shortcut settings<\/button>/)
  assert.match(html, /max-w-\[440px\]/)

  assert.match(source, /from 'konsta\/react'/)
  for (const component of ['App', 'Navbar', 'Card', 'List', 'ListItem', 'Button']) {
    assert.match(source, new RegExp(`\\b${component}\\b`))
  }
})

test('shortcut setup title comes from i18n for every supported language', () => {
  const expectedTitles = {
    en: 'Set OpenFlash shortcuts',
    zh: '设置 OpenFlash 快捷键',
    fi: 'Aseta OpenFlash-pikanäppäimet',
    de: 'OpenFlash-Tastenkürzel festlegen',
  }

  for (const [language, title] of Object.entries(expectedTitles)) {
    const html = renderToStaticMarkup(createElement(ShortcutSetupApp, {
      chromeApi: { tabs: { create() {} } },
      navigatorApi: { language },
    }))
    assert.match(html, new RegExp(title))
  }
  assert.match(source, /t\('shortcutSetup\.title'\)/)
})

test('shortcut settings action only opens the browser shortcut settings page', async () => {
  const calls = []
  const chromeApi = {
    tabs: {
      create(payload) {
        calls.push(payload)
        return Promise.resolve({ id: 7 })
      },
    },
  }

  const result = await openShortcutSettings(chromeApi)

  assert.deepEqual(calls, [{ url: 'chrome://extensions/shortcuts' }])
  assert.deepEqual(result, { id: 7 })
})
