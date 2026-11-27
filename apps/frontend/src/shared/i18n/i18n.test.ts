import { describe, expect, it } from 'vitest'
import { createInstance } from 'i18next'
import { resources } from '@cloudhub/i18n'
import { initialLocale } from './i18n'

function keysAtMostTwoLevels(value: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, entry]) => {
    expect(key).not.toContain('.')
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof entry === 'string') return [path]
    expect(prefix).toBe('')
    expect(entry).toBeTypeOf('object')
    expect(entry).not.toBeNull()
    expect(Array.isArray(entry)).toBe(false)
    return keysAtMostTwoLevels(entry as Record<string, unknown>, path)
  })
}

describe('shared translations', () => {
  it('keeps matching keys at no more than two levels', () => {
    expect(keysAtMostTwoLevels(resources.en.translation).sort())
      .toEqual(keysAtMostTwoLevels(resources.vi.translation).sort())
  })

  it('keeps the stored language or detects Vietnamese browser locales', () => {
    expect(initialLocale(null, 'vi-VN')).toBe('vi')
    expect(initialLocale(null, 'fr-FR')).toBe('en')
    expect(initialLocale('en', 'vi-VN')).toBe('en')
  })

  it('translates, pluralizes and falls back through i18next', async () => {
    const i18n = createInstance()
    await i18n.init({ resources, lng: 'vi', fallbackLng: 'en' })
    expect(i18n.t('dashboard')).toBe('Tổng quan')
    expect(i18n.t('filterByProvider')).toBe('Lọc theo dịch vụ')
    expect(i18n.t('resultCount', { count: 2 })).toBe('2 kết quả')
    expect(i18n.t('resultCount', { count: 1, lng: 'en' })).toBe('1 result')
    expect(i18n.t('resultCount', { count: 2, lng: 'en' })).toBe('2 results')
    i18n.removeResourceBundle('vi', 'translation')
    expect(i18n.t('dashboard')).toBe('Overview')
  })
})
