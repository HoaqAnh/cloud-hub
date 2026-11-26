import { apiRequest } from '@cloudhub/api-client'
import type { AuthUser } from '@cloudhub/contracts'

export const authApi = {
  me: () => apiRequest<AuthUser>('/api/auth/me'),
  login: (username: string, password: string) =>
    apiRequest<AuthUser>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    }),
  logout: () =>
    apiRequest<AuthUser>('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    }),
}
