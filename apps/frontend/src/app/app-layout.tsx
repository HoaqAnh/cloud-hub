import {
  Bell,
  ChevronDown,
  Cloud,
  Files,
  LayoutDashboard,
  Languages,
  LogOut,
  Search,
} from 'lucide-react'
import { Link, useRouterState } from '@tanstack/react-router'
import { Alert, AlertDescription, Avatar, AvatarFallback, Button, Separator } from '@cloudhub/ui'
import type { AuthUser } from '@cloudhub/contracts'
import type { Locale, Translate } from '@cloudhub/i18n'
import type { ReactNode } from 'react'

interface AppLayoutProps {
  user: AuthUser
  locale: Locale
  t: Translate
  onChangeLocale: (locale: Locale) => void
  onLogout: () => void
  isLoggingOut: boolean
  logoutError: Error | null
  children: ReactNode
}

const navigation = [
  { to: '/dashboard' as const, label: 'dashboard' as const, icon: LayoutDashboard },
  { to: '/explorer' as const, label: 'explorer' as const, icon: Files },
]

export function AppLayout({
  user,
  locale,
  t,
  onChangeLocale,
  onLogout,
  isLoggingOut,
  logoutError,
  children,
}: AppLayoutProps) {
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const username = user.username || 'CloudHub user'

  return (
    <div className="min-h-screen bg-[#f7f7fb] text-slate-900">
      <a
        href="#main-content"
        className="sr-only fixed left-4 top-4 z-50 rounded-md bg-white px-4 py-2 text-sm font-medium text-slate-900 shadow-md focus-visible:not-sr-only focus-visible:ring-2 focus-visible:ring-violet-600"
      >
        {t('skipToContent')}
      </a>
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col bg-[#111019] px-4 py-6 text-white lg:flex">
        <Link to="/dashboard" className="flex items-center gap-3 px-3">
          <span className="grid size-10 place-items-center overflow-hidden rounded-xl bg-violet-500/10">
            <Cloud className="size-5 text-violet-300" />
          </span>
          <span className="text-lg font-semibold tracking-tight">CloudHub</span>
        </Link>

        <div className="mt-10 px-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500">
          {t('workspace')}
        </div>
        <nav className="mt-3 space-y-1">
          {navigation.map(({ to, label, icon: Icon }) => {
            const active = pathname === to
            return (
              <Link
                key={to}
                to={to}
                aria-current={active ? 'page' : undefined}
                className={`flex h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors ${
                  active
                    ? 'bg-violet-500/15 text-violet-200'
                    : 'text-slate-400 hover:bg-white/5 hover:text-white'
                }`}
              >
                <Icon className={`size-4.5 ${active ? 'text-violet-300' : ''}`} />
                {t(label)}
              </Link>
            )
          })}
        </nav>

        <div className="mt-auto rounded-2xl border border-white/10 bg-white/4 p-4">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-200">
            <Cloud className="size-4 text-violet-300" />
            {t('cloudTogether')}
          </div>
          <p className="mt-2 text-xs leading-5 text-slate-500">
            {t('cloudTogetherDescription')}
          </p>
        </div>
      </aside>

      <header className="fixed inset-x-0 top-0 z-20 flex h-16 items-center justify-between border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur-xl lg:left-64 lg:px-8">
        <div className="flex items-center gap-3">
          <Link to="/dashboard" className="flex items-center gap-2 lg:hidden">
            <Cloud className="size-5 text-violet-600" />
            <span className="font-semibold">CloudHub</span>
          </Link>
          <div className="hidden items-center gap-2 text-sm text-slate-400 sm:flex">
            <Search className="size-4" />
            <span>{t('workspaceSearch')}</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            aria-label={locale === 'en' ? t('switchToVietnamese') : t('switchToEnglish')}
            onClick={() => onChangeLocale(locale === 'en' ? 'vi' : 'en')}
            className="size-9 text-slate-500 hover:bg-violet-50 hover:text-violet-700"
          >
            <Languages className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label={t('notifications')}
            className="hidden size-9 text-slate-500 hover:bg-violet-50 hover:text-violet-700 sm:inline-flex"
          >
            <Bell className="size-4" />
          </Button>
          <Separator orientation="vertical" className="mx-1 hidden h-7 sm:block" />
          <Avatar className="size-8 border border-violet-100">
            <AvatarFallback className="bg-violet-100 text-xs font-semibold text-violet-700">
              {username.slice(0, 2).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <div className="hidden min-w-0 sm:block">
            <p className="max-w-36 truncate text-sm font-medium text-slate-800">{username}</p>
            <p className="text-xs text-slate-500">{t('personalWorkspace')}</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            disabled={isLoggingOut}
            onClick={onLogout}
            aria-label={t('signOut')}
            className="ml-1 size-9 text-slate-500 hover:bg-rose-50 hover:text-rose-600"
          >
            <LogOut className="size-4" />
          </Button>
          <ChevronDown className="hidden size-4 text-slate-400 sm:block" />
        </div>
      </header>

      <nav className="fixed inset-x-0 bottom-0 z-30 flex border-t border-slate-200 bg-white px-4 py-2 lg:hidden">
        {navigation.map(({ to, label, icon: Icon }) => {
          const active = pathname === to
          return (
            <Link
              key={to}
              to={to}
              aria-current={active ? 'page' : undefined}
              className={`flex flex-1 flex-col items-center gap-1 rounded-xl py-2 text-xs font-medium ${
                active ? 'text-violet-700' : 'text-slate-500'
              }`}
            >
              <Icon className="size-5" />
              {t(label)}
            </Link>
          )
        })}
      </nav>

      <main
        id="main-content"
        tabIndex={-1}
        className="min-h-screen px-4 pb-24 pt-20 sm:px-6 lg:ml-64 lg:px-10 lg:pb-10 lg:pt-24"
      >
        <div className="mx-auto max-w-7xl space-y-4">
          {logoutError && (
            <Alert variant="destructive" className="border-rose-200 bg-rose-50 text-rose-800">
              <AlertDescription>{logoutError.message}</AlertDescription>
            </Alert>
          )}
          {children}
        </div>
      </main>
    </div>
  )
}
