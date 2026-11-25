import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ChevronRight,
  Cloud,
  Folder,
  Grid2X2,
  HardDrive,
  Link2,
  List,
  LogOut,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
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
  capabilities?: {
    pagination: boolean
    search: boolean
    quota: boolean
    download: boolean
    upload: boolean
    createFolder: boolean
    rename: boolean
    trash: boolean
    thumbnail: boolean
  }
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
  thumbnailUrl: string | null
  previewable: boolean
}
type Breadcrumb = { id?: string; name: string }
type StorageQuota = { limit: number; usage: number; usageInDrive: number; usageInTrash: number }
type SearchGroup = {
  accountId: string
  accountName: string
  provider: string
  status: 'ok' | 'error'
  items: FileItem[]
  nextPageToken?: string
  error?: string
}

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

function providerLabel(provider: string) {
  if (provider === 'DROPBOX') return 'Dropbox'
  if (provider === 'ONEDRIVE') return 'OneDrive'
  return 'Google Drive'
}

function selectedCapabilities(accounts: Account[], selectedAccount: string | null) {
  return accounts.find((account) => account.id === selectedAccount)?.capabilities
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
  const [searchQuery, setSearchQuery] = useState('')
  const [fileType, setFileType] = useState('all')
  const [sort, setSort] = useState('name')
  const [quota, setQuota] = useState<Record<string, StorageQuota>>({})
  const [providerFilter, setProviderFilter] = useState('all')
  const [searchGroups, setSearchGroups] = useState<SearchGroup[]>([])
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
      await Promise.all(value.map(async (account) => {
        try {
          const value = await api<StorageQuota>(`/api/accounts/${account.id}/quota`)
          setQuota((current) => ({ ...current, [account.id]: value }))
        } catch {
          // Keep the account visible; quota is provider-specific and optional.
        }
      }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not load accounts')
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    if (auth?.authenticated) loadAccounts()
  }, [auth?.authenticated, loadAccounts])

  useEffect(() => {
    if (selectedAccount && route === 'explorer') {
      if (searchQuery.trim()) runSearch()
      else loadFiles(selectedAccount, folderId)
    }
  }, [fileType, sort, providerFilter])

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
      query.set('type', fileType)
      query.set('sort', sort)
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

  async function runSearch(pageToken = '', append = false) {
    if (!searchQuery.trim()) {
      setSearchGroups([])
      if (!selectedAccount) return
      await loadFiles(selectedAccount, folderId)
      return
    }
    setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams({
        q: searchQuery.trim(),
        type: fileType,
        sort,
      })
      if (providerFilter !== 'all') query.set('providers', providerFilter)
      if (pageToken) query.set('pageToken', pageToken)
      const value = await api<{ groups: SearchGroup[] }>(
        `/api/search?${query}`
      )
      setSearchGroups(value.groups)
      const allItems = value.groups.flatMap((group) => group.items)
      setFiles((current) => append ? [...current, ...allItems] : allItems)
      setNextPageToken('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Search failed')
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
      previewFile(file)
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

  function previewFile(file: FileItem) {
    if (!selectedAccount) return
    if (file.previewable) {
      const url = `/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.id)}/content?disposition=inline`
      window.open(url, '_blank', 'noopener,noreferrer')
    } else {
      downloadFile(file)
    }
  }

  async function createFolder() {
    if (!selectedAccount) return
    const name = window.prompt('Folder name')
    if (!name?.trim()) return
    try {
      const query = new URLSearchParams({ name: name.trim() })
      if (folderId) query.set('parentId', folderId)
      await api<FileItem>(`/api/accounts/${selectedAccount}/folders?${query}`, { method: 'POST' })
      await loadFiles(selectedAccount, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not create folder')
    }
  }

  async function renameFile(file: FileItem) {
    if (!selectedAccount) return
    const name = window.prompt('New name', file.name)
    if (!name?.trim() || name.trim() === file.name) return
    try {
      await api<FileItem>(
        `/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.id)}?name=${encodeURIComponent(name.trim())}`,
        { method: 'PATCH' }
      )
      await loadFiles(selectedAccount, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not rename item')
    }
  }

  async function trashFile(file: FileItem) {
    if (!selectedAccount) return
    const account = accounts.find((item) => item.id === selectedAccount)
    if (!window.confirm(`Move "${file.name}" to ${account ? providerLabel(account.provider) : 'cloud'} trash?`)) return
    try {
      await api<{ trashed: boolean }>(
        `/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.id)}`,
        { method: 'DELETE' }
      )
      await loadFiles(selectedAccount, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not move item to trash')
    }
  }

  async function disconnectAccount(accountId: string) {
    const account = accounts.find((item) => item.id === accountId)
    if (!window.confirm(`Disconnect this ${account ? providerLabel(account.provider) : 'cloud'} account and delete its local tokens?`)) return
    try {
      await api<{ disconnected: boolean }>(`/api/accounts/${accountId}`, { method: 'DELETE' })
      await loadAccounts()
      setSelectedAccount(null)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not disconnect account')
    }
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
              <div className="flex gap-2">
                <a href="/api/oauth/google/start" className="inline-flex h-10 items-center gap-2 rounded-md bg-cyan-500 px-4 text-sm font-medium text-slate-950 hover:bg-cyan-400"><Cloud className="h-4 w-4" />Google Drive</a>
                <a href="/api/oauth/dropbox/start" className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-700 px-4 text-sm font-medium text-slate-200 hover:border-cyan-500"><Cloud className="h-4 w-4 text-cyan-400" />Dropbox</a>
                <a href="/api/oauth/onedrive/start" className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-700 px-4 text-sm font-medium text-slate-200 hover:border-cyan-500"><Cloud className="h-4 w-4 text-cyan-400" />OneDrive</a>
              </div>
            </div>
            {error && <p className="mt-6 rounded-lg border border-rose-900 bg-rose-950/30 p-3 text-sm text-rose-300">{error}</p>}
            <div className="mt-8 grid gap-4 sm:grid-cols-2">
              {accounts.map((account) => (
                <div key={account.id} className="rounded-xl border border-slate-800 bg-slate-900 p-5">
                  <button onClick={() => {
                    setSelectedAccount(account.id)
                    setBreadcrumbs([{ name: 'My Drive' }])
                    navigate('explorer')
                    loadFiles(account.id)
                  }} className="w-full text-left">
                    <div className="flex items-start justify-between">
                      <div className="grid h-10 w-10 place-items-center rounded-lg bg-cyan-500/10 text-cyan-400"><HardDrive /></div>
                      <div className="flex items-center gap-2">
                        <span className="rounded-full border border-slate-700 px-2 py-1 text-xs text-slate-300">{providerLabel(account.provider)}</span>
                        <span className="rounded-full bg-emerald-500/10 px-2 py-1 text-xs text-emerald-400">{account.status}</span>
                      </div>
                    </div>
                    <h2 className="mt-4 font-medium">{account.displayName}</h2><p className="mt-1 text-sm text-slate-400">{account.email || 'Google account'}</p>
                    {quota[account.id] && (
                      <div className="mt-4">
                        <div className="mb-2 flex justify-between text-xs text-slate-400"><span>Drive storage</span><span>{formatSize(quota[account.id].usage)} / {quota[account.id].limit > 0 ? formatSize(quota[account.id].limit) : 'unlimited'}</span></div>
                        <div className="h-2 overflow-hidden rounded-full bg-slate-800"><div className="h-full bg-cyan-500" style={{ width: `${quota[account.id].limit > 0 ? Math.min(100, quota[account.id].usage / quota[account.id].limit * 100) : 0}%` }} /></div>
                      </div>
                    )}
                  </button>
                  <Button variant="ghost" className="mt-4 w-full text-rose-400" onClick={() => disconnectAccount(account.id)}><Trash2 className="h-4 w-4" />Disconnect</Button>
                </div>
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
                  <Button
                    onClick={createFolder}
                    disabled={selectedCapabilities(accounts, selectedAccount)?.createFolder === false}
                  ><Plus className="h-4 w-4" />New folder</Button>
                  <Button
                    onClick={uploadFile}
                    disabled={uploading || selectedCapabilities(accounts, selectedAccount)?.upload === false}
                  ><Upload className="h-4 w-4" />Upload</Button>
                  <Button onClick={() => selectedAccount && loadFiles(selectedAccount, folderId)}><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Refresh</Button>
                </div>
              )}
            </div>
            {accounts.length > 0 && (
              <div className="mt-6 flex flex-wrap items-center gap-2">
                {accounts.map((account) => (
                  <button
                    key={account.id}
                    onClick={() => {
                      setSelectedAccount(account.id)
                      setBreadcrumbs([{ name: `${providerLabel(account.provider)} root` }])
                      setSearchQuery('')
                      loadFiles(account.id)
                    }}
                    className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
                      selectedAccount === account.id
                        ? 'border-cyan-500 bg-cyan-500/10 text-cyan-300'
                        : 'border-slate-700 bg-slate-900 text-slate-300 hover:border-slate-600'
                    }`}
                  >
                    <HardDrive className="h-4 w-4" />
                    <span>{account.displayName}</span>
                    <span className="rounded-full border border-slate-700 px-1.5 py-0.5 text-[10px] text-slate-400">{providerLabel(account.provider)}</span>
                  </button>
                ))}
              </div>
            )}

            <div className="mt-6 flex gap-2">
              <input
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                onKeyDown={(event) => { if (event.key === 'Enter') runSearch() }}
                placeholder="Search cloud files by name"
                className="min-w-0 flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm outline-none focus:border-cyan-500"
              />
              <select value={fileType} onChange={(event) => { setFileType(event.target.value); }} className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm">
                <option value="all">All types</option>
                <option value="folder">Folders</option>
                <option value="image">Images</option>
                <option value="pdf">PDF</option>
                <option value="file">Files</option>
              </select>
              <select value={sort} onChange={(event) => setSort(event.target.value)} className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm">
                <option value="name">Name A-Z</option>
                <option value="name-desc">Name Z-A</option>
                <option value="size">Size small</option>
                <option value="size-desc">Size large</option>
                <option value="modified">Modified old</option>
                <option value="modified-desc">Modified new</option>
              </select>
              <select value={providerFilter} onChange={(event) => setProviderFilter(event.target.value)} className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-sm">
                <option value="all">All providers</option>
                <option value="GOOGLE">Google Drive</option>
                <option value="DROPBOX">Dropbox</option>
                <option value="ONEDRIVE">OneDrive</option>
              </select>
            </div>

            {searchQuery.trim() && (
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                {searchGroups.map((group) => (
                  <button
                    key={group.accountId}
                    onClick={() => {
                      setSelectedAccount(group.accountId)
                      setBreadcrumbs([{ name: `${providerLabel(group.provider)} root` }])
                    }}
                    className="rounded-lg border border-slate-800 bg-slate-900 p-3 text-left"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="truncate text-sm font-medium">{group.accountName}</span>
                      <span className="rounded-full border border-slate-700 px-2 py-0.5 text-[10px] text-slate-400">{providerLabel(group.provider)}</span>
                    </div>
                    {group.status === 'error' ? (
                      <p className="mt-2 text-xs text-rose-400">{group.error || 'Search failed'}</p>
                    ) : group.items.length === 0 ? (
                      <p className="mt-2 text-xs text-slate-500">No results</p>
                    ) : (
                      <p className="mt-2 text-xs text-cyan-400">{group.items.length} result{group.items.length === 1 ? '' : 's'}</p>
                    )}
                  </button>
                ))}
                {!loading && searchGroups.length === 0 && (
                  <p className="col-span-full rounded-lg border border-slate-800 p-3 text-sm text-slate-500">Searching providers...</p>
                )}
              </div>
            )}

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
                <div className="grid grid-cols-[1fr_180px_140px_104px] border-b border-slate-800 bg-slate-900 px-4 py-3 text-xs uppercase tracking-wider text-slate-500"><span>Name</span><span>Modified</span><span>Size</span><span /></div>
                {loading && <p className="p-8 text-center text-slate-400">Loading files...</p>}
                {!loading && files.map(file => (
                  <div key={file.id} className="grid w-full grid-cols-[1fr_180px_140px_104px] items-center border-b border-slate-800 px-4 py-3 text-sm last:border-0 hover:bg-slate-900">
                    <button onClick={() => enterFolder(file)} className="flex items-center gap-3 text-left">
                      {file.shortcut ? <Link2 className="h-5 w-5 text-violet-400" /> : file.type === 'FOLDER' ? <Folder className="h-5 w-5 text-cyan-400" /> : <HardDrive className="h-5 w-5 text-slate-400" />}
                      <span className="truncate">{file.name}{file.nativeDoc ? ' (Google Doc)' : ''}</span>
                    </button>
                    <span className="text-slate-400">{file.modifiedAt ? new Date(file.modifiedAt).toLocaleString() : '-'}</span>
                    <span className="text-slate-400">{formatSize(file.size)}</span>
                    <span className="flex justify-end gap-1">
                      <button onClick={() => renameFile(file)} disabled={selectedCapabilities(accounts, selectedAccount)?.rename === false} className="rounded p-2 text-slate-500 hover:bg-slate-800 hover:text-cyan-300 disabled:cursor-not-allowed disabled:opacity-40" aria-label={`Rename ${file.name}`}><Pencil className="h-4 w-4" /></button>
                      <button onClick={() => trashFile(file)} disabled={selectedCapabilities(accounts, selectedAccount)?.trash === false} className="rounded p-2 text-slate-500 hover:bg-slate-800 hover:text-rose-400 disabled:cursor-not-allowed disabled:opacity-40" aria-label={`Trash ${file.name}`}><Trash2 className="h-4 w-4" /></button>
                    </span>
                  </div>
                ))}
                {!loading && !files.length && <p className="p-8 text-center text-slate-400">{selectedAccount ? 'This folder is empty.' : 'Connect a cloud account to browse files.'}</p>}
              </div>
            ) : (
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {loading && <p className="col-span-full p-8 text-center text-slate-400">Loading files...</p>}
                {!loading && files.map(file => (
                  <div key={file.id} className="min-h-36 rounded-xl border border-slate-800 bg-slate-900 p-4 hover:border-cyan-700">
                    <button onClick={() => enterFolder(file)} className="w-full text-left">
                      <div className="flex h-20 items-center justify-center overflow-hidden rounded-lg bg-slate-950">
                        {file.thumbnailUrl && selectedAccount
                          ? <img
                              src={`/api/accounts/${selectedAccount}/files/${encodeURIComponent(file.id)}/thumbnail`}
                              alt=""
                              className="h-full w-full object-cover"
                            />
                          : file.shortcut
                            ? <Link2 className="h-8 w-8 text-violet-400" />
                            : file.type === 'FOLDER'
                              ? <Folder className="h-8 w-8 text-cyan-400" />
                              : <HardDrive className="h-8 w-8 text-slate-400" />}
                      </div>
                      <p className="mt-3 truncate text-sm font-medium">{file.name}</p>
                      <p className="mt-2 text-xs text-slate-500">{formatSize(file.size)} · {file.modifiedAt ? new Date(file.modifiedAt).toLocaleDateString() : '-'}</p>
                    </button>
                    <div className="mt-2 flex justify-end gap-1">
                      <button onClick={() => renameFile(file)} disabled={selectedCapabilities(accounts, selectedAccount)?.rename === false} className="rounded p-2 text-slate-500 hover:bg-slate-800 hover:text-cyan-300 disabled:cursor-not-allowed disabled:opacity-40" aria-label={`Rename ${file.name}`}><Pencil className="h-4 w-4" /></button>
                      <button onClick={() => trashFile(file)} disabled={selectedCapabilities(accounts, selectedAccount)?.trash === false} className="rounded p-2 text-slate-500 hover:bg-slate-800 hover:text-rose-400 disabled:cursor-not-allowed disabled:opacity-40" aria-label={`Trash ${file.name}`}><Trash2 className="h-4 w-4" /></button>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {nextPageToken && (
              <Button className="mt-4 w-full" onClick={() => searchQuery.trim() ? runSearch(nextPageToken, true) : selectedAccount && loadFiles(selectedAccount, folderId, nextPageToken, true)} disabled={loading}>
                {loading ? 'Loading...' : 'Load more'}
              </Button>
            )}
          </div>
        )}
      </section>
    </main>
  )
}
