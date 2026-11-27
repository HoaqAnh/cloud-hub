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

  it('fetches a session CSRF token before sending a mutation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }))
      .mockResolvedValueOnce(Response.json({ moved: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/api/transfers/example/retry-source', { method: 'POST' }))
      .resolves.toEqual({ moved: true })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    expect(fetchMock.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('test-token')
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
