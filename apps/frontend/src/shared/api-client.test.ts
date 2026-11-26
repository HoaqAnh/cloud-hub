import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest } from '../../../../packages/api-client/src/index'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('shared API client', () => {
  it('sends cookie credentials and parses JSON responses', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ authenticated: true }), {
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest<{ authenticated: boolean }>('/api/auth/me')).resolves.toEqual({
      authenticated: true,
    })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('exposes API error status and provider error code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ detail: 'Quota exceeded', code: 'LIMIT_REACHED' }), {
        status: 429,
        headers: { 'Content-Type': 'application/json' },
      }),
    ))

    await expect(apiRequest('/api/files')).rejects.toMatchObject({
      name: 'ApiError',
      message: 'Quota exceeded [LIMIT_REACHED]',
      status: 429,
      code: 'LIMIT_REACHED',
    } satisfies Partial<ApiError>)
  })
})
