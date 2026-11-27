import en from '../locales/en.json'
import vi from '../locales/vi.json'

export const resources = { en: { translation: en }, vi: { translation: vi } }
export type Locale = keyof typeof resources
export type TranslationKey = keyof typeof en | 'resultCount'
export type Translate = (key: TranslationKey, options?: { count: number }) => string
