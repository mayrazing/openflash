import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './locales/zh.json' with { type: 'json' }
import en from './locales/en.json' with { type: 'json' }
import fi from './locales/fi.json' with { type: 'json' }
import de from './locales/de.json' with { type: 'json' }

const SUPPORTED = ['zh', 'en', 'fi', 'de']
const osLang = navigator.language?.slice(0, 2)
export const detectedLang = SUPPORTED.includes(osLang) ? osLang : 'en'

i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
    fi: { translation: fi },
    de: { translation: de },
  },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export default i18n
