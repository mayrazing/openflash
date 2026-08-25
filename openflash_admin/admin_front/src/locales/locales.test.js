import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const LOCALES = ['zh', 'en', 'fi', 'de']

function flattenKeys(value, prefix = '') {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key
    return child && typeof child === 'object' && !Array.isArray(child)
      ? flattenKeys(child, path)
      : path
  }).sort()
}

async function loadLocale(locale) {
  const source = await readFile(new URL(`./${locale}.json`, import.meta.url), 'utf8')
  return JSON.parse(source)
}

test('zh, en, fi and de locale files expose identical key sets', async () => {
  const dictionaries = await Promise.all(LOCALES.map(loadLocale))
  const expected = flattenKeys(dictionaries[0])

  for (let index = 1; index < dictionaries.length; index += 1) {
    assert.deepEqual(flattenKeys(dictionaries[index]), expected, `${LOCALES[index]} locale keys differ`)
  }
})

test('base locales cover shell, auth, status and error text', async () => {
  const dictionary = await loadLocale('en')
  const keys = flattenKeys(dictionary)

  for (const required of [
    'app.title',
    'auth.login',
    'auth.logout',
    'nav.overview',
    'nav.users',
    'nav.cli',
    'nav.platformAi',
    'pages.users.title',
    'pages.users.cliNames.codex',
    'pages.cli.title',
    'pages.platformAi.title',
    'pages.platformAi.runtime.AVAILABLE',
    'pages.platformAi.runtime.ERROR',
    'pages.platformAi.runtime.UNAVAILABLE',
    'common.loading',
    'common.saved',
    'errors.generic',
    'errors.forbidden',
  ]) {
    assert.ok(keys.includes(required), `missing base locale key: ${required}`)
  }
})

test('all locales cover platform AI access and destructive management labels', async () => {
  const dictionaries = await Promise.all(LOCALES.map(loadLocale))
  const required = [
    'platformProvided', 'runtimeUnavailable', 'defaultDeny', 'allUsers', 'selectedUsers',
    'credentialConfigured', 'discoverModels', 'deleteConfirmTitle', 'deleteConfirmAction',
    'apiSectionTitle', 'apiSectionDescription', 'emptyApiTitle', 'emptyApiDescription',
  ]

  for (const [index, dictionary] of dictionaries.entries()) {
    assert.equal(typeof dictionary.pages.users.platformProvided, 'string')
    assert.equal(typeof dictionary.pages.users.runtimeUnavailable, 'string')
    assert.equal(typeof dictionary.pages.platformAi.runtime.UNAVAILABLE, 'string')
    assert.notEqual(dictionary.pages.platformAi.runtime.UNAVAILABLE, 'UNAVAILABLE')
    for (const key of required.slice(2)) {
      assert.equal(
        typeof dictionary.pages.platformAi[key],
        'string',
        `${LOCALES[index]} missing pages.platformAi.${key}`,
      )
    }
  }
})

test('all locales cover user account status, destructive actions and errors', async () => {
  const dictionaries = await Promise.all(LOCALES.map(loadLocale))
  const required = [
    'activeStatus',
    'bannedStatus',
    'banUser',
    'unbanUser',
    'deleteUser',
    'deleteConfirmTitle',
    'deleteConfirmBody',
    'deleteCancel',
    'deleteConfirmAction',
    'deleting',
    'accountActionError',
    'selfAccountActionDisabled',
    'selfAccountMutationError',
  ]

  for (const [index, dictionary] of dictionaries.entries()) {
    for (const key of required) {
      assert.equal(
        typeof dictionary.pages.users[key],
        'string',
        `${LOCALES[index]} missing pages.users.${key}`,
      )
      assert.ok(dictionary.pages.users[key].trim(), `${LOCALES[index]} has empty pages.users.${key}`)
    }
    assert.match(dictionary.pages.users.deleteConfirmBody, /{{user}}/)
  }
})

test('English user delete confirmation uses a single-word action', async () => {
  const dictionary = await loadLocale('en')

  assert.equal(dictionary.pages.users.deleteConfirmAction, 'Delete')
})
