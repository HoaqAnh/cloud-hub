import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Cloud, Folder, HardDrive, LogOut, RefreshCw } from 'lucide-react'
import { Button } from './components/ui/button'

type AuthUser = { authenticated: boolean; username?: string }
type Account = {
  id: string
  provider: string
  displayName: string
  email: string
  status: string
}
type FileItem = {
  id: string
  name: string
  type: 'FILE' | 'FOLDER'
  mimeType: string
  size: number | null
  modifiedAt: string | null
}

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `Request failed (${response.status})`)
  }
  return response.json()
}

export default function App() {
  const [auth, setAuth] = useState<AuthUser | null>(null)
  const [route, setRoute] = useState(() =>
    window.location.pathname === '/explorer' ? 'explorer' : 'dashboard')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccount, setSelectedAccount] = useState<string | null>(null)
  const [folderId, setFolderId] = useState<string | undefined>()
  const [files, setFiles] = useState<FileItem[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api<AuthUser>('/api/auth/me')
      .then((value) => {
        setAuth(value)
        if (!value.authenticated) window.history.replaceState({}, '', '/login')
        else window.history.replaceState({}, '', `/${route}`)
      })
      .catch(() => setAuth({ authenticated: false }))
  }, [])

  useEffect(() => {
    if (auth?.authenticated) {
      loadAccounts()
    }
  }, [auth?.authenticated])

  async function loadAccounts() {
    try {
      setError('')
      const value = await api<Account[]>('/api/accounts')
      setAccounts(value)
      if (!selectedAccount && value.length) setSelectedAccount(value[0].id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load accounts')
    }
  }

  async function loadFiles(accountId: string, parentId?: string) {
    setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams()
      if (parentId) query.set('folderId', parentId)
      const value = await api<{ items: FileItem[] }>(
        `/api/accounts/${accountId}/files${query.size ? `?${query}` : ''}`)
      setFiles(value.items)
      setFolderId(parentId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load files')
    } finally {
      setLoading(false)
    }
  }

  function navigate(next: 'dashboard' | 'explorer' | 'login') {
    setRoute(next)
    window.history.pushState({}, '', `/${next}`)
  }

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setError('')
    try {
      const value = await api<AuthUser>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          username: form.get('username'),
          password: form.get('password'),
        }),
      })
      setAuth(value)
      navigate('dashboard')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Invalid credentials')
    }
  }

  async function logout() {
    await api<{ authenticated: boolean }>('/api/auth/logout', { method: 'POST', body: '{}' })
    setAuth({ authenticated: false })
    setAccounts([])
    navigate('login')
  }

  if (auth === null) {
    return <main className="grid min-h-screen place-items-center text-slate-400">Loading...</main>
  }

  if (!auth.authenticated) {
    return (
      <main className="grid min-h-screen place-items-center px-6">
        <form onSubmit={login} className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
          <div className="flex items-center gap-3">
            <div className="grid h-11 w-11 place-items-center rounded-xl bg-cyan-500/10 text-cyan-400">
              <Cloud />
            </div>
            <div><h1 className="text-2xl font-semibold">CloudHub</h1><p className="text-sm text-slate-400">Sign in to your portal</p></div>
          </div>
          <label className="mt-8 block text-sm">Username
            <input name="username" required className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 outline-none focus:border-cyan-500" />
          </label>
          <label className="mt-4 block text-sm">Password
            <input name="password" type="password" required className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 outline-none focus:border-cyan-500" />
          </label>
          {error && <p className="mt-4 text-sm text-rose-400">{error}</p>}
          <Button type="submit" className="mt-6 w-full">Sign in</Button>
        </form>
      </main>
    )
  }

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100">
      <aside className="fixed bottom-0 left-0 top-0 w-64 border-r border-slate-800 bg-slate-900 p-4">
        <div className="flex items-center gap-3 px-2 py-3"><Cloud className="text-cyan-400" /><span className="font-semibold">CloudHub</span></div>
        <nav className="mt-6 space-y-1">
          <button onClick={() => navigate('dashboard')} className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${route === 'dashboard' ? 'bg-cyan-500/10 text-cyan-300' : 'text-slate-400 hover:bg-slate-800'}`}><HardDrive className="h-4 w-4" />Dashboard</button>
          <button onClick={() => {
            navigate('explorer')
            if (selectedAccount) loadFiles(selectedAccount)
          }} className={`flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm ${route === 'explorer' ? 'bg-cyan-500/10 text-cyan-300' : 'text-slate-400 hover:bg-slate-800'}`}><Folder className="h-4 w-4" />Explorer</button>
        </nav>
      </aside>
      <header className="fixed left-64 right-0 top-0 z-10 flex h-16 items-center justify-between border-b border-slate-800 bg-slate-950/95 px-8">
        <p className="text-sm text-slate-400">Welcome, <span className="text-slate-200">{auth.username}</span></p>
        <Button onClick={logout}><LogOut className="h-4 w-4" />Logout</Button>
      </header>
      <section className="ml-64 pt-16">
        {route === 'dashboard' ? (
          <div className="mx-auto max-w-5xl p-8">
            <div className="flex items-end justify-between">
              <div><p className="text-sm text-cyan-400">Accounts</p><h1 className="mt-1 text-3xl font-semibold">Dashboard</h1></div>
              <a href="/api/oauth/google/start" className="inline-flex h-10 items-center gap-2 rounded-md bg-cyan-500 px-4 text-sm font-medium text-slate-950 hover:bg-cyan-400"><Cloud className="h-4 w-4" />Connect Google Drive</a>
            </div>
            {error && <p className="mt-6 rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">{error}</p>}
            <div className="mt-8 grid gap-4 sm:grid-cols-2">
              {accounts.map((account) => (
                <button key={account.id} onClick={() => { setSelectedAccount(account.id); navigate('explorer'); loadFiles(account.id) }} className="rounded-xl border border-slate-800 bg-slate-900 p-5 text-left hover:border-cyan-700">
                  <div className="flex items-start justify-between"><div className="grid h-10 w-10 place-items-center rounded-lg bg-cyan-500/10 text-cyan-400"><HardDrive /></div><span className="rounded-full bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">{account.status}</span></div>
                  <h2 className="mt-4 font-medium">{account.displayName}</h2><p className="mt-1 text-sm text-slate-400">{account.email || 'Google account'}</p>
                </button>
              ))}
              {!accounts.length && <div className="col-span-full rounded-xl border border-dashed border-slate-700 p-10 text-center text-slate-400">No cloud accounts connected yet.</div>}
            </div>
          </div>
        ) : (
          <div className="mx-auto max-w-5xl p-8">
            <div className="flex items-end justify-between">
              <div><p className="text-sm text-cyan-400">Files</p><h1 className="mt-1 text-3xl font-semibold">Explorer</h1></div>
              {selectedAccount && <Button onClick={() => loadFiles(selectedAccount, folderId)}><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Refresh</Button>}
            </div>
            {accounts.length > 1 && <select value={selectedAccount ?? ''} onChange={(event) => { setSelectedAccount(event.target.value); loadFiles(event.target.value) }} className="mt-6 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2">{accounts.map(account => <option key={account.id} value={account.id}>{account.displayName}</option>)}</select>}
            {error && <p className="mt-6 rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">{error}</p>}
            <div className="mt-6 overflow-hidden rounded-xl border border-slate-800">
              <div className="grid grid-cols-[1fr_180px_140px] border-b border-slate-800 bg-slate-900 px-4 py-3 text-xs uppercase tracking-wider text-slate-500"><span>Name</span><span>Modified</span><span>Size</span></div>
              {loading && <p className="p-8 text-center text-slate-400">Loading files...</p>}
              {!loading && files.map(file => (
                <button key={file.id} onClick={() => file.type === 'FOLDER' && selectedAccount && loadFiles(selectedAccount, file.id)} className="grid w-full grid-cols-[1fr_180px_140px] items-center border-b border-slate-800 px-4 py-3 text-left text-sm last:border-0 hover:bg-slate-900">
                  <span className="flex items-center gap-3">{file.type === 'FOLDER' ? <Folder className="h-5 w-5 text-cyan-400" /> : <HardDrive className="h-5 w-5 text-slate-400" />}<span>{file.name}</span></span>
                  <span className="text-slate-400">{file.modifiedAt ? new Date(file.modifiedAt).toLocaleDateString() : '-'}</span>
                  <span className="text-slate-400">{file.size == null ? '-' : `${Math.round(file.size / 1024)} KB`}</span>
                </button>
              ))}
              {!loading && !files.length && <p className="p-8 text-center text-slate-400">{selectedAccount ? 'This folder is empty.' : 'Connect Google Drive to browse files.'}</p>}
            </div>
          </div>
        )}
      </section>
    </main>
  )
}
