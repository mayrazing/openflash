import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { resetLanguage, setLanguage, t } from '../src/i18n.js'

const appSource = readFileSync(new URL('../src/popup/PopupApp.jsx', import.meta.url), 'utf8')
const viewSource = readFileSync(new URL('../src/popup/PopupView.jsx', import.meta.url), 'utf8')
const source = `${appSource}\n${viewSource}`
const html = readFileSync(new URL('../popup.html', import.meta.url), 'utf8')

test('popup loads and uses extension i18n', () => {
  assert.match(appSource, /from '\.\.\/i18n\.js'/)
  assert.match(source, /api\.settings\(serviceUrl\)/)
  assert.match(source, /const loadLanguageSafely = useCallback\(async/)
  assert.match(source, /const lang = setLanguage\(settings\?\.language\)/)
  assert.match(source, /t\('popup\.serviceUrl'\)/)
  assert.match(source, /t\('popup\.aiPromptTitleWithDeck'/)
})

test('popup does not keep hard-coded Chinese UI labels', () => {
  assert.doesNotMatch(source, />服务地址</)
  assert.doesNotMatch(source, />登录</)
  assert.doesNotMatch(source, /placeholder="卡包名称"/)
  assert.doesNotMatch(source, />保存提示词</)
  assert.doesNotMatch(source, /AI 提示词.*：/)
})

test('popup html defaults to English language', () => {
  assert.match(html, /<html lang="en">/)
  assert.match(html, /<body class="popup-page">/)
})

test('popup routes every dynamic result through the shared page HUD presenter', () => {
  assert.match(appSource, /from '\.\.\/popupNotification\.js'/)
  assert.match(appSource, /createPopupStatusPresenter\(next, createPopupNotifier\(chromeApi\.runtime\)\)/)
  assert.match(appSource, /const presentError = useCallback/)
  assert.match(appSource, /await presentError\(error\)/)
  assert.match(appSource, /await presentStatus\('success', t\('popup\.saved'\)\)/)
})

test('popup shortcut labels are translated', () => {
  try {
    setLanguage('zh')
    assert.equal(t('popup.shortcutsTitle'), '快捷键')
    assert.equal(t('popup.shortcutImportDefault'), '直接导入选中内容')
    assert.equal(t('popup.shortcutManualCard'), '快速手动建卡')
  } finally {
    resetLanguage()
  }
})

test('selected deck marker is translated in every supported popup language', () => {
  try {
    const expected = {
      en: 'Selected',
      zh: '已选中',
      fi: 'Valittu',
      de: 'Ausgewählt',
    }
    for (const [lang, text] of Object.entries(expected)) {
      setLanguage(lang)
      assert.equal(t('popup.selectedDeck'), text)
    }
  } finally {
    resetLanguage()
  }
})

test('delete deck confirmation copy is translated in every supported popup language', () => {
  try {
    const expected = {
      en: { title: 'Delete deck', cancel: 'Cancel' },
      zh: { title: '删除卡包', cancel: '取消' },
      fi: { title: 'Poista pakka', cancel: 'Peruuta' },
      de: { title: 'Deck löschen', cancel: 'Abbrechen' },
    }
    for (const [lang, text] of Object.entries(expected)) {
      setLanguage(lang)
      assert.equal(t('popup.deleteDeckConfirmTitle'), text.title)
      assert.equal(t('popup.cancel'), text.cancel)
      // 正文必须把卡包名填进去, 否则用户看不出要删的是哪个卡包。
      const body = t('popup.deleteDeckConfirmBody', { deckName: 'Spanish' })
      assert.match(body, /Spanish/)
      assert.doesNotMatch(body, /\{\{deckName\}\}/)
    }
  } finally {
    resetLanguage()
  }
})

test('shortcutText falls back to popup.shortcutUnset when shortcut is empty, not to a suggested key', () => {
  // PopupApp 依赖浏览器环境，故按现有架构用源码正则校验快捷键回退分支。
  // 核心断言：shortcutText 不接受 always-truthy 的 fallback 参数，空 shortcut 必须落到 popup.shortcutUnset。
  assert.match(source, /function shortcutText\(commandName\) {/)
  assert.doesNotMatch(source, /function shortcutText\(commandName,\s*fallback\)/)
  assert.match(source, /return command\?\.shortcut \|\| t\('popup\.shortcutUnset'\)/)
  // 调用点不得传入建议键作为 fallback，否则空 shortcut 会被建议键覆盖、shortcutUnset 不可达。
  assert.doesNotMatch(source, /shortcutText\('openflash-import-default',\s*'Alt\+Shift\+D'\)/)
  assert.doesNotMatch(source, /shortcutText\('openflash-manual-card',\s*'Alt\+Shift\+A'\)/)
  // shortcutUnset 文案在四语言下均有定义，确保空 shortcut 路径有真实翻译可显示。
  try {
    for (const lang of ['en', 'zh', 'fi', 'de']) {
      setLanguage(lang)
      assert.notEqual(t('popup.shortcutUnset'), 'popup.shortcutUnset')
    }
  } finally {
    resetLanguage()
  }
})

test('popup exposes browser shortcut settings entry', () => {
  assert.match(source, /from '\.\.\/browserShortcutSettings\.js'/)
  assert.match(source, /t\('popup\.shortcutSettingsAction'\)/)
  assert.match(source, /openBrowserShortcutSettings\(chromeApi\.tabs\)/)
})

test('successful session load clears a stale login error before rendering the popup', () => {
  assert.match(
    source,
    /updateState\(\(current\) => \(\{ \.\.\.current, user, error: '' \}\)\)/,
  )
})

test('shortcut settings entry labels are translated in every supported popup language', () => {
  try {
    const expected = {
      en: 'Set shortcuts',
      zh: '设置快捷键',
      fi: 'Aseta pikanäppäimet',
      de: 'Tastenkürzel festlegen',
    }
    for (const [lang, text] of Object.entries(expected)) {
      setLanguage(lang)
      assert.equal(t('popup.shortcutSettingsButton'), text)
      assert.notEqual(t('popup.shortcutBrowserTip'), 'popup.shortcutBrowserTip')
    }
  } finally {
    resetLanguage()
  }
})
