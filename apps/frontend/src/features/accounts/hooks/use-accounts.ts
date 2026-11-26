import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Account, StorageQuota } from '@cloudhub/contracts'
import { accountsApi } from '../api/accounts-api'

export const accountsQueryKey = ['accounts'] as const

interface AccountsData {
  accounts: Account[]
  quotas: Record<string, StorageQuota>
  quotaErrors: Record<string, string>
}

export function useAccounts() {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: accountsQueryKey,
    queryFn: async (): Promise<AccountsData> => {
      const accounts = await accountsApi.list()
      const quotaResults = await Promise.all(
        accounts.map(async (account) => {
          try {
            return { accountId: account.id, quota: await accountsApi.quota(account.id) } as const
          } catch (reason) {
            return {
              accountId: account.id,
              error: reason instanceof Error ? reason.message : 'Quota unavailable',
            } as const
          }
        }),
      )
      const quotas: Record<string, StorageQuota> = {}
      const quotaErrors: Record<string, string> = {}
      for (const result of quotaResults) {
        if ('quota' in result && result.quota) {
          quotas[result.accountId] = result.quota
        } else if ('error' in result && result.error) {
          quotaErrors[result.accountId] = result.error
        }
      }

      return {
        accounts,
        quotas,
        quotaErrors,
      }
    },
  })

  const disconnectMutation = useMutation({
    mutationFn: accountsApi.disconnect,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountsQueryKey }),
  })

  return {
    accounts: query.data?.accounts ?? [],
    quotas: query.data?.quotas ?? {},
    quotaErrors: query.data?.quotaErrors ?? {},
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
    disconnect: disconnectMutation.mutate,
    disconnectError: disconnectMutation.error,
  }
}
