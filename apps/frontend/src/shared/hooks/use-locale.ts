import { useTranslation } from 'react-i18next'
import type { Locale, Translate } from '@cloudhub/i18n'
import { localeStorageKey } from '../i18n/i18n'

export function useLocale() {
  const { i18n, t: translate } = useTranslation()
  const locale: Locale = i18n.resolvedLanguage === 'vi' ? 'vi' : 'en'

  function setLocale(nextLocale: Locale) {
    window.localStorage.setItem(localeStorageKey, nextLocale)
    void i18n.changeLanguage(nextLocale)
  }

  const t: Translate = (key, options) => translate(key, options ?? {})
  return { locale, setLocale, t }
}
