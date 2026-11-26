import { useContext, useState } from 'react'
import { detectLocale, translate, type Locale, type TranslationKey } from '@cloudhub/i18n'
import { LocaleContext } from '../i18n/locale-context'

const localeStorageKey = 'cloudhub:locale'

export function useLocale() {
  const context = useContext(LocaleContext)
  const [locale, setLocaleState] = useState<Locale>(() => {
    const stored = window.localStorage.getItem(localeStorageKey)
    const detected = stored === 'en' || stored === 'vi' ? stored : detectLocale()
    document.documentElement.lang = detected
    return detected
  })

  function setLocale(nextLocale: Locale) {
    window.localStorage.setItem(localeStorageKey, nextLocale)
    document.documentElement.lang = nextLocale
    setLocaleState(nextLocale)
  }

  function t(key: TranslationKey) {
    return translate(locale, key)
  }

  return context ?? { locale, setLocale, t }
}
