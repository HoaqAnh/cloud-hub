import { createContext } from 'react'
import type { Locale, TranslationKey } from '@cloudhub/i18n'

export interface LocaleContextValue {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: TranslationKey) => string
}

export const LocaleContext = createContext<LocaleContextValue | null>(null)
