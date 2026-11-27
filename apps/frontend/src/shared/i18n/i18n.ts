import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import { resources, type Locale } from '@cloudhub/i18n'

export const localeStorageKey = 'cloudhub:locale'

export function initialLocale(stored: string | null, browserLanguage: string): Locale {
  if (stored === 'en' || stored === 'vi') return stored
  return browserLanguage.toLowerCase().split('-')[0] === 'vi' ? 'vi' : 'en'
}

export async function initializeI18n() {
  const lng = initialLocale(window.localStorage.getItem(localeStorageKey), navigator.language)
  i18next.on('languageChanged', (language) => {
    document.documentElement.lang = language
  })
  await i18next.use(initReactI18next).init({
    resources,
    lng,
    fallbackLng: 'en',
    supportedLngs: ['en', 'vi'],
    interpolation: { escapeValue: false },
  })
}
