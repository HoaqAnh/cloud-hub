import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ChevronRight,
  Cloud,
  Download,
  Folder,
  Grid2X2,
  HardDrive,
  Link2,
  List,
  LogOut,
  RefreshCw,
  Upload,
} from 'lucide-react'
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
  shortcut: boolean
  shortcutTargetId: string | null
  nativeDoc: boolean
}
type Breadcrumb = { id?: string; name: string }

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
  })
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      message = body.detail || body.title || message
      if (body.code) message = `${message} [${body.code}]`
    } catch {
      const text = await response.text()
      if (text) message = text
    }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

function formatSize(size: number | null) {
  if (size == null) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export default function App() {
  const [auth, setAuth] = useState<AuthUser | null>(null)
  const [route, setRoute] = useState(() =>
    window.location.pathname === '/explorer' ? 'explorer' : 'dashboard')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccount, setSelectedAccount] = useState<string | null>(null)
  const [folderId, setFolderId] = useState<string | undefined>()
  const [breadcrumbs, setBreadcrumbs] = useState<Breadcrumb[]>([{ name: 'My Drive' }])
  const [files, setFiles] = useState<FileItem[]>([])
  const [nextPageToken, setNextPageToken] = useState('')
  const [view, setView] = useState<'grid' | 'list'>('list')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    api<AuthUser>('/api/auth/me')
      .then((value) => {
        setAuth(value)
        if (!value.authenticated) window.history.replaceState({}, '', '/login')
        else window.history.replaceState({}, '', window.location.pathname === '/explorer' ? '/explorer' : '/dashboard')
      })
      .catch(() => setAuth({ authenticated: false }))
  }, [])

  const loadAccounts = useCallback(async () => {
    try {
      setError('')
      const value = await api<Account[]>('/api/accounts')
      setAccounts(value)
      setSelectedAccount((current) => current ?? (value.length ? value[0].id : null))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load accounts')
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    if (auth?.authenticated) loadAccounts()
  }, [auth?.authenticated, loadAccounts])

  async function loadFiles(
    accountId: string,
    parentId?: string,
    pageToken = '',
    append = false
  ) {
    setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams()
      if (parentId) query.set('folderId', parentId)
      if (pageToken) query.set('pageToken', pageToken)
      const value = await api<{ items: FileItem[]; nextPageToken: string }>(
        `/api/accounts/${accountId}/files${query.size ? `?${query}` : ''}`)
      setFiles((current) => append ? [...current, ...value.items] : value.items)
      setNextPageToken(value.nextPageToken)
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
        headers: { 'Content-Type': 'application/json' },
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
    await api<{ authenticated: boolean }>('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    })
    setAuth({ authenticated: false })
    setAccounts([])
    navigate('login')
  }

  function enterFolder(file: FileItem) {
    if (!selectedAccount) return
    if (file.shortcut) {
      resolveShortcut(file)
      return
    }
    if (file.type !== 'FOLDER') {
      downloadFile(file)
      return
    }
    setBreadcrumbs((current) => [...current, { id: file.id, name: file.name }])
    loadFiles(selectedAccount, file.id)
  }

  async function resolveShortcut(file: FileItem) {
    if (!selectedAccount || !file.shortcutTargetId) return
    try {
      setLoading(true)
      const target = await api<FileItem>(
        `/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.shortcutTargetId)}`
      )
      if (target.type === 'FOLDER') {
        setBreadcrumbs((current) => [...current, { id: target.id, name: target.name }])
        await loadFiles(selectedAccount, target.id)
      } else {
        downloadFile(target)
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not resolve shortcut')
      setLoading(false)
    }
  }

  function navigateBreadcrumb(index: number) {
    if (!selectedAccount) return
    const target = breadcrumbs[index]
    setBreadcrumbs(breadcrumbs.slice(0, index + 1))
    loadFiles(selectedAccount, target.id)
  }

  function downloadFile(file: FileItem) {
    if (!selectedAccount) return
    const url = `/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.id)}/content`
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  function uploadFile() {
    fileInput.current?.click()
  }

  async function handleUpload(file: File | undefined) {
    if (!file || !selectedAccount) return
    setUploading(true)
    setUploadProgress(0)
    setError('')

    const form = new FormData()
    form.append('file', file)
    const query = folderId ? `?parentId=${encodeURIComponent(folderId)}` : ''
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `/api/accounts/${selectedAccount}/files${query}`)
    xhr.withCredentials = true
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) setUploadProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onload = () => {
      setUploading(false)
      setUploadProgress(100)
      if (xhr.status >= 200 && xhr.status < 300) {
        loadFiles(selectedAccount, folderId)
      } else {
        setError(`Upload failed (${xhr.status})`)
      }
    }
    xhr.onerror = () => {
      setUploading(false)
      setError('Network error while uploading')
    }
    xhr.send(form)
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
            if (selectedAccount) {
              setBreadcrumbs([{ name: 'My Drive' }])
              loadFiles(selectedAccount)
            }
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
                <button key={account.id} onClick={() => {
                  setSelectedAccount(account.id)
                  setBreadcrumbs([{ name: 'My Drive' }])
                  navigate('explorer')
                  loadFiles(account.id)
                }} className="rounded-xl border border-slate-800 bg-slate-900 p-5 text-left hover:border-cyan-700">
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
              {selectedAccount && (
                <div className="flex gap-2">
                  <input
                    ref={fileInput}
                    type="file"
                    className="hidden"
                    onChange={(event) => handleUpload(event.target.files?.[0])}
                  />
                  <Button onClick={uploadFile} disabled={uploading}><Upload className="h-4 w-4" />Upload</Button>
                  <Button onClick={() => selectedAccount && loadFiles(selectedAccount, folderId)}><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Refresh</Button>
                </div>
              )}
            </div>
            {accounts.length > 1 && <select value={selectedAccount ?? ''} onChange={(event) => {
              setSelectedAccount(event.target.value)
              setBreadcrumbs([{ name: 'My Drive' }])
              loadFiles(event.target.value)
            }} className="mt-6 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2">{accounts.map(account => <option key={account.id} value={account.id}>{account.displayName}</option>)}</select>}

            <div className="mt-6 flex items-center justify-between">
              <nav className="flex items-center gap-1 text-sm">
                {breadcrumbs.map((item, index) => (
                  <span key={`${item.id ?? 'root'}-${index}`} className="flex items-center">
                    {index > 0 && <ChevronRight className="mx-1 h-4 w-4 text-slate-600" />}
                    <button onClick={() => navigateBreadcrumb(index)} className={index === breadcrumbs.length - 1 ? 'text-cyan-300' : 'text-slate-400 hover:text-slate-200'}>{item.name}</button>
                  </span>
                ))}
              </nav>
              <div className="flex rounded-lg border border-slate-800 p-1">
                <button onClick={() => setView('list')} className={`rounded px-2 py-1 ${view === 'list' ? 'bg-slate-800 text-cyan-300' : 'text-slate-500'}`} aria-label="List view"><List className="h-4 w-4" /></button>
                <button onClick={() => setView('grid')} className={`rounded px-2 py-1 ${view === 'grid' ? 'bg-slate-800 text-cyan-300' : 'text-slate-500'}`} aria-label="Grid view"><Grid2X2 className="h-4 w-4" /></button>
              </div>
            </div>

            {uploading && (
              <div className="mt-4 rounded-lg border border-cyan-900 bg-cyan-950/20 p-3">
                <div className="mb-2 flex justify-between text-xs text-cyan-300"><span>Uploading</span><span>{uploadProgress}%</span></div>
                <div className="h-2 overflow-hidden rounded-full bg-slate-800"><div className="h-full bg-cyan-500 transition-all" style={{ width: `${uploadProgress}%` }} /></div>
              </div>
            )}
            {error && <p className="mt-4 rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">{error}</p>}

            {view === 'list' ? (
              <div className="mt-4 overflow-hidden rounded-xl border border-slate-800">
                <div className="grid grid-cols-[1fr_180px_140px_52px] border-b border-slate-800 bg-slate-900 px-4 py-3 text-xs uppercase tracking-wider text-slate-500"><span>Name</span><span>Modified</span><span>Size</span><span /></div>
                {loading && <p className="p-8 text-center text-slate-400">Loading files...</p>}
                {!loading && files.map(file => (
                  <button key={file.id} onClick={() => enterFolder(file)} className="grid w-full grid-cols-[1fr_180px_140px_52px] items-center border-b border-slate-800 px-4 py-3 text-left text-sm last:border-0 hover:bg-slate-900">
                    <span className="flex items-center gap-3">
                      {file.shortcut ? <Link2 className="h-5 w-5 text-violet-400" /> : file.type === 'FOLDER' ? <Folder className="h-5 w-5 text-cyan-400" /> : <HardDrive className="h-5 w-5 text-slate-400" />}
                      <span className="truncate">{file.name}{file.nativeDoc ? ' (Google Doc)' : ''}</span>
                    </span>
                    <span className="text-slate-400">{file.modifiedAt ? new Date(file.modifiedAt).toLocaleString() : '-'}</span>
                    <span className="text-slate-400">{formatSize(file.size)}</span>
                    {file.type === 'FILE' && <Download className="h-4 w-4 text-slate-500" />}
                    {file.type === 'FOLDER' && <span />}
                  </button>
                ))}
                {!loading && !files.length && <p className="p-8 text-center text-slate-400">{selectedAccount ? 'This folder is empty.' : 'Connect Google Drive to browse files.'}</p>}
              </div>
            ) : (
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {loading && <p className="col-span-full p-8 text-center text-slate-400">Loading files...</p>}
                {!loading && files.map(file => (
                  <button key={file.id} onClick={() => enterFolder(file)} className="min-h-36 rounded-xl border border-slate-800 bg-slate-900 p-4 text-left hover:border-cyan-700">
                    <div className="flex items-center justify-between">
                      {file.shortcut ? <Link2 className="h-8 w-8 text-violet-400" /> : file.type === 'FOLDER' ? <Folder className="h-8 w-8 text-cyan-400" /> : <HardDrive className="h-8 w-8 text-slate-400" />}
                      {file.type === 'FILE' && <Download className="h-4 w-4 text-slate-500" />}
                    </div>
                    <p className="mt-4 truncate text-sm font-medium">{file.name}</p>
                    <p className="mt-2 text-xs text-slate-500">{formatSize(file.size)} · {file.modifiedAt ? new Date(file.modifiedAt).toLocaleDateString() : '-'}</p>
                  </button>
                ))}
              </div>
            )}
            {nextPageToken && (
              <Button className="mt-4 w-full" onClick={() => selectedAccount && loadFiles(selectedAccount, folderId, nextPageToken, true)} disabled={loading}>
                {loading ? 'Loading...' : 'Load more'}
              </Button>
            )}
          </div>
        )}
      </section>
    </main>
  )
}
