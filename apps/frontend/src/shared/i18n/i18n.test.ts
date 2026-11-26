import { describe, expect, it } from 'vitest'
import { translate } from '@cloudhub/i18n'

describe('shared translations', () => {
  it('provides the same UI key in English and Vietnamese', () => {
    expect(translate('en', 'dashboard')).toBe('Overview')
    expect(translate('vi', 'dashboard')).toBe('Tổng quan')
  })
})
