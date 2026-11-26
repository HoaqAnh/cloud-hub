import { Alert, AlertDescription } from '@cloudhub/ui'
import { ExplorerPage } from '../features/explorer/components/explorer-page'
import { useAccounts } from '../features/accounts/hooks/use-accounts'
import { useLocale } from '../shared/hooks/use-locale'

export default function ExplorerRouteView() {
  const locale = useLocale()
  const accounts = useAccounts()
  return (
    <div className="space-y-4">
      {accounts.error && (
        <Alert variant="destructive" className="border-rose-200 bg-rose-50 text-rose-800">
          <AlertDescription>{accounts.error.message}</AlertDescription>
        </Alert>
      )}
      <ExplorerPage
        accounts={accounts.accounts}
        accountsLoading={accounts.isLoading}
        t={locale.t}
      />
    </div>
  )
}
