import i18n from '../i18n.js'

export function getErrorMessage(code) {
  return i18n.t(`errors.${code}`, { defaultValue: i18n.t('errors.default') })
}

export function getKnownErrorMessage(code) {
  if (code == null) return null
  const msg = i18n.t(`errors.${code}`, { defaultValue: '' })
  return msg || null
}
