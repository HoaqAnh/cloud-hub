import { describe, expect, it } from 'vitest'
import { translate } from '@cloudhub/i18n'

describe('shared translations', () => {
  it('provides the same UI key in English and Vietnamese', () => {
    expect(translate('en', 'dashboard')).toBe('Overview')
    expect(translate('vi', 'dashboard')).toBe('Tổng quan')
  })

  it('localizes Explorer sort labels and accessible control names', () => {
    const sortKeys = [
      'sortNameAsc',
      'sortNameDesc',
      'sortSizeAsc',
      'sortSizeDesc',
      'sortModifiedAsc',
      'sortModifiedDesc',
    ] as const

    expect(sortKeys.map((key) => translate('en', key))).toEqual([
      'Name A–Z',
      'Name Z–A',
      'Size: smallest first',
      'Size: largest first',
      'Modified: oldest first',
      'Modified: newest first',
    ])
    expect(translate('en', 'filterByProvider')).toBe('Filter by provider')

    expect(sortKeys.map((key) => translate('vi', key))).toEqual([
      'Tên A–Z',
      'Tên Z–A',
      'Kích thước: nhỏ nhất trước',
      'Kích thước: lớn nhất trước',
      'Chỉnh sửa: cũ nhất trước',
      'Chỉnh sửa: mới nhất trước',
    ])
    expect(translate('vi', 'filterByProvider')).toBe('Lọc theo dịch vụ')
  })
})
