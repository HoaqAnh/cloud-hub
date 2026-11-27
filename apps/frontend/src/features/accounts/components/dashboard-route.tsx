import { useNavigate } from '@tanstack/react-router'
import type { Account } from '@cloudhub/contracts'
import type { Translate } from '@cloudhub/i18n'
import { DashboardPage } from './dashboard-page'
import { useAccounts } from '../hooks/use-accounts'

interface DashboardRouteProps {
  t: Translate
}

export function DashboardRoute({ t }: DashboardRouteProps) {
  const navigate = useNavigate()
  const state = useAccounts()

  function disconnect(account: Account) {
    if (window.confirm(`${t('disconnectConfirm')} ${account.displayName} ${t('removeLocalTokens')}`)) {
      state.disconnect(account.id)
    }
  }

  function browse(account: Account) {
    window.localStorage.setItem('cloudhub:selected-account', account.id)
    void navigate({ to: '/explorer' })
  }

  return (
    <DashboardPage
      accounts={state.accounts}
      quotas={state.quotas}
      quotaErrors={state.quotaErrors}
      isLoading={state.isLoading}
      error={state.error ?? state.disconnectError}
      t={t}
      onRefresh={() => void state.refetch()}
      onDisconnect={disconnect}
      onBrowse={browse}
    />
  )
}
