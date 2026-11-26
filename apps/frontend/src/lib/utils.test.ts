import { describe, expect, it } from 'vitest'
import { cn } from '../../../../packages/ui/src/lib/utils'

describe('cn', () => {
  it('merges Tailwind classes', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4')
  })
})
