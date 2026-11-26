import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth-api'

export const authQueryKey = ['auth'] as const

export function useAuth() {
  const queryClient = useQueryClient()
  const authQuery = useQuery({
    queryKey: authQueryKey,
    queryFn: authApi.me,
    retry: false,
    refetchOnWindowFocus: false,
  })

  const loginMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      authApi.login(username, password),
    onSuccess: (user) => queryClient.setQueryData(authQueryKey, user),
  })

  const logoutMutation = useMutation({
    mutationFn: authApi.logout,
    onSuccess: (user) => queryClient.setQueryData(authQueryKey, user),
  })

  return {
    user: authQuery.data,
    isLoading: authQuery.isLoading,
    error: authQuery.error,
    retry: authQuery.refetch,
    login: loginMutation.mutate,
    loginError: loginMutation.error,
    isLoggingIn: loginMutation.isPending,
    logout: logoutMutation.mutate,
    logoutError: logoutMutation.error,
    isLoggingOut: logoutMutation.isPending,
  }
}
