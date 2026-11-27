export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function getCsrfToken(signal?: AbortSignal | null) {
  return apiRequest<{ headerName: string; token: string }>('/api/auth/csrf', { cache: 'no-store', signal })
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData) && !headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }

  const method = (options.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrf = await getCsrfToken(options.signal)
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(path, { credentials: 'include', ...options, headers })

  if (!response.ok) {
    const body = await response.text()
    let message = body || `Request failed (${response.status})`
    let code: string | undefined

    if (body) {
      try {
        const detail = JSON.parse(body) as {
          detail?: string
          title?: string
          code?: string
        }
        message = detail.detail || detail.title || message
        code = detail.code
      } catch {
        // Keep the response body as the error message when it is not JSON.
      }
    }

    if (code) message = `${message} [${code}]`
    throw new ApiError(message, response.status, code)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
