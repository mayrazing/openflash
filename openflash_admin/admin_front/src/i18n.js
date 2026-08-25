import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './locales/zh.json' with { type: 'json' }
import en from './locales/en.json' with { type: 'json' }
import fi from './locales/fi.json' with { type: 'json' }
import de from './locales/de.json' with { type: 'json' }

const SUPPORTED_LOCALES = ['zh', 'en', 'fi', 'de']
const browserLocale = globalThis.navigator?.language?.slice(0, 2)
export const detectedLocale = SUPPORTED_LOCALES.includes(browserLocale) ? browserLocale : 'en'

i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
    fi: { translation: fi },
    de: { translation: de },
  },
  lng: detectedLocale,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

if (globalThis.document?.documentElement) {
  globalThis.document.documentElement.lang = i18n.resolvedLanguage || detectedLocale
}

export default i18n
