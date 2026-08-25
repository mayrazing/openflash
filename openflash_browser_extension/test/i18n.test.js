import assert from 'node:assert/strict'
import test from 'node:test'
import { getLanguage, resetLanguage, setLanguage, t } from '../src/i18n.js'

test('i18n defaults to English', () => {
  resetLanguage()

  assert.equal(getLanguage(), 'en')
  assert.equal(t('popup.serviceUrl'), 'Service URL')
})

test('i18n switches supported languages', () => {
  resetLanguage()
  setLanguage('zh')

  assert.equal(getLanguage(), 'zh')
  assert.equal(t('popup.serviceUrl'), '服务地址')
})

test('i18n contains full German and Finnish popup messages', () => {
  resetLanguage()
  setLanguage('de')
  assert.equal(t('popup.saveAi'), 'Prompt speichern')
  assert.equal(t('import.success'), 'In OpenFlash importiert')

  setLanguage('fi')
  assert.equal(t('popup.saveAi'), 'Tallenna kehote')
  assert.equal(t('import.success'), 'Tuotu OpenFlashiin')
})

test('mock layout headings and disabled-plugin status exist in every supported language', () => {
  const expected = {
    zh: ['卡包', '插件未启用'],
    en: ['Decks', 'Plugin disabled'],
    fi: ['Pakat', 'Liitännäinen ei käytössä'],
    de: ['Decks', 'Plugin deaktiviert'],
  }
  try {
    for (const [language, messages] of Object.entries(expected)) {
      setLanguage(language)
      assert.equal(t('popup.decksTitle'), messages[0])
      assert.equal(t('popup.aiPluginDisabled'), messages[1])
    }
  } finally {
    resetLanguage()
  }
})

test('i18n falls back to English for unsupported languages', () => {
  resetLanguage()
  setLanguage('ja')

  assert.equal(getLanguage(), 'en')
  assert.equal(t('popup.login'), 'Log in')
})

test('i18n returns key when translation is missing', () => {
  resetLanguage()

  assert.equal(t('missing.key'), 'missing.key')
})

test('i18n interpolates template parameters', () => {
  resetLanguage()
  assert.equal(t('import.partialImageFailed', { count: 3 }), 'Imported, 3 image(s) failed')
  assert.equal(t('popup.aiPromptTitleWithDeck', { title: 'AI prompt', deckName: 'Demo' }), 'AI prompt · Demo')

  setLanguage('zh')
  assert.equal(t('import.partialImageFailed', { count: 3 }), '已导入，3 张图片失败')
  assert.equal(t('popup.aiPromptTitleWithDeck', { title: 'AI 提示词', deckName: '示例' }), 'AI 提示词 · 示例')
})

test('i18n handles null params without crashing', () => {
  resetLanguage()
  assert.equal(t('popup.login', null), 'Log in')
})

test('page notification has saved and partial-saved text in every supported language', () => {
  try {
    const expected = {
      zh: ['已保存', '已保存，3 张图片失败'],
      en: ['Saved', 'Saved, 3 image(s) failed'],
      fi: ['Tallennettu', 'Tallennettu, 3 kuvan tallennus epäonnistui'],
      de: ['Gespeichert', 'Gespeichert, 3 Bild(er) fehlgeschlagen'],
    }
    for (const [language, messages] of Object.entries(expected)) {
      setLanguage(language)
      assert.equal(t('notification.saved'), messages[0])
      assert.equal(t('notification.partialSaved', { count: 3 }), messages[1])
    }
  } finally {
    resetLanguage()
  }
})

test('manual card messages exist in every supported language', () => {
  const expected = {
    zh: '快速手动建卡',
    en: 'Quick manual card',
    fi: 'Nopea manuaalikortti',
    de: 'Schnelle manuelle Karte',
  }
  try {
    for (const [language, message] of Object.entries(expected)) {
      setLanguage(language)
      assert.equal(t('manualCard.title'), message)
      assert.notEqual(t('manualCard.save'), 'manualCard.save')
      assert.notEqual(t('manualCard.emptyContent'), 'manualCard.emptyContent')
      assert.notEqual(t('manualCard.unsupportedPage'), 'manualCard.unsupportedPage')
      assert.notEqual(t('popup.shortcutsTitle'), 'popup.shortcutsTitle')
      assert.notEqual(t('popup.shortcutManualCard'), 'popup.shortcutManualCard')
      assert.notEqual(t('popup.shortcutUnset'), 'popup.shortcutUnset')
    }
  } finally {
    resetLanguage()
  }
})
