import { useEffect } from 'react'
import { Outlet, useNavigate, useRouterState } from '@tanstack/react-router'
import { Alert, AlertDescription, Button } from '@cloudhub/ui'
import type { TranslationKey } from '@cloudhub/i18n'
import { AppLayout } from './app-layout'
import { useAuth } from '../features/auth/hooks/use-auth'
import { LoginPage } from '../features/auth/components/login-page'
import { useLocale } from '../shared/hooks/use-locale'
import { LocaleProvider } from '../shared/providers/locale-provider'

function LoadingPage({ label }: { label: string }) {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f7f7fb]">
      <div className="flex items-center gap-3 text-sm text-slate-500">
        <span className="size-5 animate-spin rounded-full border-2 border-violet-200 border-t-violet-600" />
        {label}
      </div>
    </main>
  )
}

function AuthErrorPage({
  error,
  onRetry,
  t,
}: {
  error: Error
  onRetry: () => void
  t: (key: TranslationKey) => string
}) {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f7f7fb] px-6">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
        <h1 className="text-lg font-semibold text-slate-900">{t('unableToVerifySession')}</h1>
        <Alert variant="destructive" className="mt-4 border-rose-200 bg-rose-50 text-rose-800">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
        <Button onClick={onRetry} className="mt-5 bg-violet-600 text-white hover:bg-violet-700">
          {t('tryAgain')}
        </Button>
      </div>
    </main>
  )
}

export function AppRoot() {
  const auth = useAuth()
  const locale = useLocale()
  const navigate = useNavigate()
  const pathname = useRouterState({ select: (state) => state.location.pathname })

  useEffect(() => {
    if (auth.user?.authenticated && pathname === '/login') {
      void navigate({ to: '/dashboard', replace: true })
    } else if (auth.user && !auth.user.authenticated && pathname !== '/login') {
      void navigate({ to: '/login', replace: true })
    }
  }, [auth.user, navigate, pathname])

  if (auth.isLoading) return <LoadingPage label={locale.t('loadingCloudHub')} />
  if (auth.error) {
    return <AuthErrorPage error={auth.error} onRetry={() => void auth.retry()} t={locale.t} />
  }
  if (!auth.user?.authenticated) {
    return (
      <LocaleProvider value={locale}>
        <LoginPage
          locale={locale.locale}
          t={locale.t}
          error={auth.loginError}
          isLoading={auth.isLoggingIn}
          onLogin={(username, password) => auth.login({ username, password })}
        />
      </LocaleProvider>
    )
  }
  if (pathname === '/login') return <LoadingPage label={locale.t('loadingCloudHub')} />

  return (
    <LocaleProvider value={locale}>
      <AppLayout
        user={auth.user}
        locale={locale.locale}
        t={locale.t}
        onChangeLocale={locale.setLocale}
        onLogout={auth.logout}
        isLoggingOut={auth.isLoggingOut}
        logoutError={auth.logoutError}
      >
        <Outlet />
      </AppLayout>
    </LocaleProvider>
  )
}
