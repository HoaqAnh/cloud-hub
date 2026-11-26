import { apiRequest } from '@cloudhub/api-client'
import type { Account, StorageQuota } from '@cloudhub/contracts'

export const accountsApi = {
  list: () => apiRequest<Account[]>('/api/accounts'),
  quota: (accountId: string) =>
    apiRequest<StorageQuota>(`/api/accounts/${accountId}/quota`),
  disconnect: (accountId: string) =>
    apiRequest<{ disconnected: boolean }>(`/api/accounts/${accountId}`, {
      method: 'DELETE',
    }),
}
