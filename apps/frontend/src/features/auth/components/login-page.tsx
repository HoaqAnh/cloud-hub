import type { FormEvent } from 'react'
import { Cloud, LockKeyhole } from 'lucide-react'
import type { ApiError } from '@cloudhub/api-client'
import { Alert, AlertDescription, Button, Input } from '@cloudhub/ui'
import heroImage from '../../../assets/hero.png'
import type { Locale, Translate } from '@cloudhub/i18n'

interface LoginPageProps {
  locale: Locale
  t: Translate
  error: Error | null
  isLoading: boolean
  onLogin: (username: string, password: string) => void
}

export function LoginPage({ locale, t, error, isLoading, onLogin }: LoginPageProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    onLogin(String(formData.get('username') ?? ''), String(formData.get('password') ?? ''))
  }

  const errorMessage = error?.message ?? ''
  const isInvalidCredentials = (error as ApiError | null)?.status === 401

  return (
    <main className="grid min-h-screen bg-[#111019] text-white lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden border-r border-white/10 lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_55%_48%,rgba(124,58,237,0.2),transparent_58%)]" />
        <div className="relative flex items-center gap-3">
          <img src={heroImage} alt="" className="size-10 object-contain" />
          <span className="text-lg font-semibold tracking-tight">CloudHub</span>
        </div>
        <div className="relative mx-auto flex max-w-lg flex-col items-center text-center">
          <img src={heroImage} alt="" className="mb-12 w-64 object-contain drop-shadow-[0_24px_70px_rgba(139,92,246,0.18)]" />
          <p className="text-sm font-medium uppercase tracking-[0.24em] text-violet-300">One space. Every cloud.</p>
          <h1 className="mt-5 text-4xl font-semibold leading-tight tracking-tight">Your files, beautifully in sync.</h1>
          <p className="mt-4 max-w-sm text-base leading-7 text-slate-400">
            Bring your cloud storage together and keep your digital workspace in order.
          </p>
        </div>
        <p className="relative text-xs text-slate-500">Secure access to your connected storage.</p>
      </section>

      <section className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-10 flex items-center gap-3 lg:hidden">
            <img src={heroImage} alt="" className="size-10 object-contain" />
            <span className="text-lg font-semibold">CloudHub</span>
          </div>
          <div className="mb-8">
            <div className="mb-5 grid size-12 place-items-center rounded-2xl border border-violet-300/20 bg-violet-500/10 text-violet-300">
              <LockKeyhole className="size-5" />
            </div>
            <h2 className="text-2xl font-semibold tracking-tight">{t('welcomeBack')}</h2>
            <p className="mt-2 text-sm text-slate-400">{t('signInDescription')}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <label className="block space-y-2 text-sm font-medium text-slate-200">
              {t('username')}
              <Input
                autoComplete="username"
                name="username"
                required
                className="h-11 border-white/10 bg-white/5 text-white placeholder:text-slate-500 focus-visible:border-violet-400 focus-visible:ring-violet-400"
              />
            </label>
            <label className="block space-y-2 text-sm font-medium text-slate-200">
              {t('password')}
              <Input
                autoComplete="current-password"
                name="password"
                required
                type="password"
                className="h-11 border-white/10 bg-white/5 text-white placeholder:text-slate-500 focus-visible:border-violet-400 focus-visible:ring-violet-400"
              />
            </label>
            {error && (
              <Alert variant="destructive" className="border-rose-400/20 bg-rose-400/10 text-rose-200">
                <AlertDescription>
                  {isInvalidCredentials
                    ? locale === 'vi' ? 'Tên đăng nhập hoặc mật khẩu không đúng.' : 'The username or password is incorrect.'
                    : errorMessage}
                </AlertDescription>
              </Alert>
            )}
            <Button
              type="submit"
              disabled={isLoading}
              className="h-11 w-full bg-violet-600 text-white hover:bg-violet-500 focus-visible:ring-violet-400"
            >
              <Cloud className="size-4" />
              {isLoading ? t('loading') : t('signIn')}
            </Button>
          </form>
          <p className="mt-8 text-center text-xs leading-5 text-slate-500">{t('secureSession')}</p>
        </div>
      </section>
    </main>
  )
}
