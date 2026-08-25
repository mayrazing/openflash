import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { resetLanguage, setLanguage, t } from '../src/i18n.js'

const html = readFileSync(new URL('../shortcutSetup.html', import.meta.url), 'utf8')
const source = readFileSync(new URL('../src/shortcut/ShortcutSetupApp.jsx', import.meta.url), 'utf8')

test('shortcut setup page loads the React entry', () => {
  assert.match(html, /<html lang="en">/)
  assert.match(html, /<meta name="viewport" content="width=device-width, initial-scale=1">/)
  assert.match(html, /<main id="app"><\/main>/)
  assert.match(html, /<script type="module" src="\/src\/shortcutSetupEntry\.jsx"><\/script>/)
})

test('shortcut setup page renders translated shortcut guide and settings button', () => {
  assert.match(source, /from '\.\.\/i18n\.js'/)
  assert.match(source, /from '\.\.\/browserShortcutSettings\.js'/)
  assert.match(source, /t\('shortcutSetup\.title'\)/)
  assert.match(source, /t\('shortcutSetup\.description'\)/)
  assert.match(source, /t\('shortcutSetup\.importDefault'\)/)
  assert.match(source, /t\('shortcutSetup\.manualCard'\)/)
  assert.match(source, /document\.title = t\('shortcutSetup\.title'\)/)
  assert.match(source, /openBrowserShortcutSettings\(chromeApi\.tabs\)/)
})

test('shortcut setup page labels are translated in every supported language', () => {
  try {
    const expectedTitles = {
      en: 'Set OpenFlash shortcuts',
      zh: '设置 OpenFlash 快捷键',
      fi: 'Aseta OpenFlash-pikanäppäimet',
      de: 'OpenFlash-Tastenkürzel festlegen',
    }
    for (const [lang, title] of Object.entries(expectedTitles)) {
      setLanguage(lang)
      assert.equal(t('shortcutSetup.title'), title)
      assert.notEqual(t('shortcutSetup.description'), 'shortcutSetup.description')
      assert.notEqual(t('shortcutSetup.openButton'), 'shortcutSetup.openButton')
    }
  } finally {
    resetLanguage()
  }
})
