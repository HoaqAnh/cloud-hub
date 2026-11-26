import { useNavigate } from '@tanstack/react-router'
import {
  ArrowUpRight,
  Cloud,
  HardDrive,
  Plus,
  RefreshCw,
  ShieldCheck,
  Unplug,
} from 'lucide-react'
import { Alert, AlertDescription, Badge, Button, Card, CardContent, CardHeader, CardTitle, Progress } from '@cloudhub/ui'
import type { Account, StorageQuota } from '@cloudhub/contracts'
import type { TranslationKey } from '@cloudhub/i18n'
import { formatSize, providerLabel } from '../../../shared/lib/format'

interface DashboardPageProps {
  accounts: Account[]
  quotas: Record<string, StorageQuota>
  quotaErrors: Record<string, string>
  isLoading: boolean
  error: Error | null
  t: (key: TranslationKey) => string
  onRefresh: () => void
  onDisconnect: (account: Account) => void
  onBrowse: (account: Account) => void
}

const providers = [
  { id: 'google', label: 'connectGoogle' as const, color: 'bg-blue-50 text-blue-600', icon: Cloud },
  { id: 'dropbox', label: 'connectDropbox' as const, color: 'bg-sky-50 text-sky-600', icon: Cloud },
  { id: 'onedrive', label: 'connectOneDrive' as const, color: 'bg-indigo-50 text-indigo-600', icon: Cloud },
]

function providerUrl(providerId: string) {
  return `/api/oauth/${providerId}/start`
}

function StatCard({
  label,
  value,
  detail,
  icon: Icon,
  tint,
}: {
  label: string
  value: string
  detail: string
  icon: typeof Cloud
  tint: string
}) {
  return (
    <Card className="border-slate-200/80 bg-white shadow-sm shadow-slate-900/2">
      <CardContent className="flex items-start justify-between p-5">
        <div>
          <p className="text-sm font-medium text-slate-500">{label}</p>
          <p className="mt-3 text-2xl font-semibold tracking-tight text-slate-900">{value}</p>
          <p className="mt-1 text-xs text-slate-400">{detail}</p>
        </div>
        <span className={`grid size-10 place-items-center rounded-xl ${tint}`}>
          <Icon className="size-5" />
        </span>
      </CardContent>
    </Card>
  )
}

export function DashboardPage({
  accounts,
  quotas,
  quotaErrors,
  isLoading,
  error,
  t,
  onRefresh,
  onDisconnect,
  onBrowse,
}: DashboardPageProps) {
  const navigate = useNavigate()
  const totalUsage = Object.values(quotas).reduce((sum, quota) => sum + quota.usage, 0)
  const connectedProviders = new Set(accounts.map((account) => account.provider)).size
  const hasQuotaData = Object.keys(quotas).length > 0

  return (
    <div className="space-y-8">
      <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
        <div>
          <div className="flex items-center gap-2 text-sm font-medium text-violet-700">
            <span className="size-1.5 rounded-full bg-violet-500" />
            {t('workspaceLabel')}
          </div>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 sm:text-[2rem]">
            {t('dashboard')}
          </h1>
          <p className="mt-2 text-sm text-slate-500">{t('workspaceDescription')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            onClick={onRefresh}
            disabled={isLoading}
            className="border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
          >
            <RefreshCw className={`size-4 ${isLoading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
          <Button
            onClick={() => navigate({ to: '/explorer' })}
            className="bg-violet-600 text-white shadow-sm shadow-violet-600/20 hover:bg-violet-700"
          >
            <HardDrive className="size-4" />
            {t('allFiles')}
            <ArrowUpRight className="size-4" />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive" className="border-rose-200 bg-rose-50 text-rose-800">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <StatCard
          label={t('accounts')}
          value={String(accounts.length)}
          detail={t('cloudAccountsConnected')}
          icon={HardDrive}
          tint="bg-violet-50 text-violet-700"
        />
        <StatCard
          label={t('usedStorage')}
          value={hasQuotaData ? formatSize(totalUsage) : '—'}
          detail={t('quotaSummary')}
          icon={Cloud}
          tint="bg-blue-50 text-blue-700"
        />
        <StatCard
          label={t('activeProviders')}
          value={String(connectedProviders)}
          detail={t('providerSummary')}
          icon={ShieldCheck}
          tint="bg-emerald-50 text-emerald-700"
        />
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.55fr)_minmax(280px,0.8fr)]">
        <div>
          <div className="mb-4 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold tracking-tight text-slate-900">{t('accounts')}</h2>
              <p className="mt-1 text-sm text-slate-500">{t('storageDescription')}</p>
            </div>
            <Badge variant="secondary" className="rounded-full bg-violet-50 text-violet-700">
              {accounts.length} {t('connected')}
            </Badge>
          </div>
          {isLoading ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {[0, 1].map((item) => (
                <Card key={item} className="h-52 animate-pulse border-slate-200 bg-white">
                  <CardContent className="p-5">
                    <div className="h-10 w-10 rounded-xl bg-slate-100" />
                    <div className="mt-6 h-4 w-36 rounded bg-slate-100" />
                    <div className="mt-3 h-3 w-48 rounded bg-slate-100" />
                  </CardContent>
                </Card>
              ))}
            </div>
          ) : accounts.length ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {accounts.map((account) => {
                const quota = quotas[account.id]
                const percentage = quota?.limit
                  ? Math.min(100, (quota.usage / quota.limit) * 100)
                  : 0
                return (
                  <Card key={account.id} className="border-slate-200/80 bg-white shadow-sm shadow-slate-900/2 transition-shadow hover:shadow-md">
                    <CardHeader className="pb-3">
                      <div className="flex items-start justify-between gap-3">
                        <span className="grid size-11 place-items-center rounded-2xl bg-violet-50 text-violet-700">
                          <Cloud className="size-5" />
                        </span>
                        <Badge className="rounded-full border border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-50">
                          <span className="mr-1.5 size-1.5 rounded-full bg-emerald-500" />
                          {account.status && !['CONNECTED', 'ACTIVE'].includes(account.status.toUpperCase())
                            ? account.status
                            : t('accountStatus')}
                        </Badge>
                      </div>
                      <div className="pt-1">
                        <CardTitle className="text-base">{account.displayName}</CardTitle>
                        <p className="mt-1 truncate text-sm text-slate-500">
                          {account.email || providerLabel(account.provider)}
                        </p>
                      </div>
                    </CardHeader>
                    <CardContent>
                      <div className="mb-2 flex items-center justify-between text-xs text-slate-500">
                        <span>{t('driveStorage')}</span>
                        <span>
                          {quota
                            ? `${formatSize(quota.usage)} / ${quota.limit > 0 ? formatSize(quota.limit) : t('unlimited')}`
                            : quotaErrors[account.id] ? t('error') : '—'}
                        </span>
                      </div>
                      <Progress
                        value={percentage}
                        className="h-1.5 bg-violet-100 **:data-[slot=progress-indicator]:bg-violet-600"
                      />
                      {quotaErrors[account.id] && (
                        <p className="mt-2 truncate text-xs text-amber-700" title={quotaErrors[account.id]}>
                          {quotaErrors[account.id]}
                        </p>
                      )}
                      <div className="mt-4 flex items-center gap-2">
                        <Button
                          onClick={() => onBrowse(account)}
                          className="h-9 flex-1 bg-violet-600 text-white hover:bg-violet-700"
                        >
                          {t('explorer')}
                          <ArrowUpRight className="size-4" />
                        </Button>
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={() => onDisconnect(account)}
                          aria-label={`${t('disconnect')} ${account.displayName}`}
                          className="size-9 border-slate-200 text-slate-500 hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
                        >
                          <Unplug className="size-4" />
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          ) : (
            <Card className="border-dashed border-slate-300 bg-white">
              <CardContent className="flex flex-col items-center px-6 py-12 text-center">
                <span className="grid size-14 place-items-center rounded-2xl bg-violet-50 text-violet-600">
                  <Cloud className="size-7" />
                </span>
                <h3 className="mt-5 text-base font-semibold text-slate-900">{t('noAccounts')}</h3>
                <p className="mt-2 max-w-sm text-sm leading-6 text-slate-500">{t('connectFirst')}</p>
                <Button
                  onClick={() => document.getElementById('providers')?.scrollIntoView({ behavior: 'smooth' })}
                  className="mt-5 bg-violet-600 text-white hover:bg-violet-700"
                >
                  <Plus className="size-4" />
                  {t('connectStorage')}
                </Button>
              </CardContent>
            </Card>
          )}
        </div>

        <Card id="providers" className="border-slate-200/80 bg-white shadow-sm shadow-slate-900/2">
          <CardHeader>
            <CardTitle className="text-base">{t('connectStorage')}</CardTitle>
            <p className="text-sm leading-5 text-slate-500">{t('connectProviderDescription')}</p>
          </CardHeader>
          <CardContent className="space-y-2">
            {providers.map(({ id, label, color, icon: Icon }) => (
              <a
                key={id}
                href={providerUrl(id)}
                className="group flex items-center justify-between rounded-xl border border-slate-100 p-3 transition-colors hover:border-violet-200 hover:bg-violet-50/60"
              >
                <span className="flex items-center gap-3">
                  <span className={`grid size-10 place-items-center rounded-xl ${color}`}>
                    <Icon className="size-5" />
                  </span>
                  <span className="text-sm font-medium text-slate-800">{t(label)}</span>
                </span>
                <Plus className="size-4 text-slate-400 transition-colors group-hover:text-violet-600" />
              </a>
            ))}
            <p className="pt-3 text-xs leading-5 text-slate-400">{t('authorizationNotice')}</p>
          </CardContent>
        </Card>
      </section>
    </div>
  )
}
