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

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { Accept: 'application/json' }),
      ...options.headers,
    },
  })

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
