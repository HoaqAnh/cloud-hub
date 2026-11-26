import type { ReactNode } from 'react'
import type { LocaleContextValue } from '../i18n/locale-context'
import { LocaleContext } from '../i18n/locale-context'

export function LocaleProvider({ value, children }: { value: LocaleContextValue; children: ReactNode }) {
  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>
}
