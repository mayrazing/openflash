import assert from 'node:assert/strict'
import test from 'node:test'

test('i18n initialization exposes the actual locale on the document root', async () => {
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { language: 'fi-FI' },
  })
  globalThis.document = { documentElement: { lang: '' } }

  const { default: i18n, detectedLocale } = await import('./i18n.js')

  assert.equal(detectedLocale, 'fi')
  assert.equal(i18n.resolvedLanguage, 'fi')
  assert.equal(document.documentElement.lang, i18n.resolvedLanguage)
})
