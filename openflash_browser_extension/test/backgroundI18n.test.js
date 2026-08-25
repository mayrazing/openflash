import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('../src/background.js', import.meta.url), 'utf8')

test('background loads settings language before refreshing menus', () => {
  assert.match(source, /from '\.\/i18n\.js'/)
  assert.match(source, /async function loadLanguageFromSettings/)
  assert.match(source, /function ensureLanguageFromSettings/)
  assert.match(source, /api\.settings\(baseUrl\)/)
  assert.match(source, /setLanguage\(settings\?\.language\)/)
  assert.match(source, /await ensureLanguageFromSettings\(\)/)
})

test('background loads language before click and command handlers use translated text', () => {
  assert.match(source, /ensureLanguageFromSettings\(\)\s*\.then\(\(\) => importMenus\.handleMenuClick\(info, tab\)\)/)
  // commands listener: ensureLanguageFromSettings() 先行, 再走 manual 优先 / import 回退, 错误 notify import.failed
  assert.match(source, /ensureLanguageFromSettings\(\)\s*\.then\(/)
  assert.match(source, /handleManualCardCommand\(command, tab\)/)
  assert.match(source, /handleCommandImport\(command, tab\)/)
  assert.match(source, /import\.failed/)
})

test('background fallback menu is translated through i18n', () => {
  assert.match(source, /title: t\('menu\.title'\)/)
  assert.doesNotMatch(source, /title: 'OpenFlash Import'/)
})

test('background routes import results through the current page notifier', () => {
  assert.match(source, /from '\.\/importNotifier\.js'/)
  assert.match(source, /const notify = createImportNotifier\(/)
  assert.match(source, /ensurePageReceiver: ensurePageNotificationReceiver/)
  assert.match(source, /async function ensurePageNotificationReceiver\(tabId\)/)
  assert.match(source, /files: \['assets\/contentScript\.js'\]/)
  assert.match(source, /notify\(error\.message \|\| t\('import\.failed'\), 'error', tab\?\.id\)/)
  assert.match(source, /t\('notification\.saved'\)/)
  assert.match(source, /t\('notification\.partialSaved'/)
  assert.match(source, /tab\.id/)
})

test('background routes popup notifications to the active page HUD', () => {
  assert.match(source, /createActiveTabNotifier/)
  assert.match(source, /message\?\.type === 'OPENFLASH_NOTIFY_ACTIVE_TAB'/)
  assert.match(
    source,
    /notifyActiveTab\(\s*message\.message,\s*message\.level,\s*isManualCardPageSender\(sender\) \? message\.sourceTabId : null,?\s*\)/,
  )
})

test('background opens shortcut setup page only on first install', () => {
  assert.match(source, /from '\.\/shortcutSetupInstall\.js'/)
  assert.match(source, /chrome\.runtime\.onInstalled\.addListener\(\(details\) =>/)
  assert.match(source, /openShortcutSetupOnInstall\(details, \{ runtime: chrome\.runtime, tabs: chrome\.tabs \}\)/)
})
