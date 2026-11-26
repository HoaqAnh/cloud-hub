import { useRef, type KeyboardEvent } from 'react'
import {
  ArrowDownAZ,
  ArrowUpDown,
  Check,
  ChevronRight,
  Cloud,
  File,
  FileImage,
  FileText,
  Folder,
  Grid2X2,
  HardDrive,
  Link2,
  List,
  Plus,
  RefreshCw,
  Search,
  Upload,
} from 'lucide-react'
import type { Account, FileItem } from '@cloudhub/contracts'
import { Alert, AlertDescription, Badge, Button, Card, Input, Progress, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@cloudhub/ui'
import type { TranslationKey } from '@cloudhub/i18n'
import { formatSize, providerLabel } from '../../../shared/lib/format'
import { explorerApi } from '../api/explorer-api'
import { useExplorer } from '../hooks/use-explorer'
import { FileActions } from './file-actions'
import { TransferDialog } from './transfer-dialog'

interface ExplorerPageProps {
  accounts: Account[]
  accountsLoading: boolean
  t: (key: TranslationKey) => string
}

function FileIcon({ file, className }: { file: FileItem; className?: string }) {
  if (file.shortcut) return <Link2 className={className} />
  if (file.type === 'FOLDER') return <Folder className={className} />
  if (file.mimeType.startsWith('image/')) return <FileImage className={className} />
  if (file.mimeType === 'application/pdf' || file.nativeDoc) return <FileText className={className} />
  return <File className={className} />
}

function FileName({ file, t }: { file: FileItem; t: (key: TranslationKey) => string }) {
  const updated = file.modifiedAt ? new Date(file.modifiedAt).toLocaleDateString() : '—'
  return (
    <span className="min-w-0">
      <span className="block truncate text-sm font-medium text-slate-800">
        {file.name}{file.nativeDoc ? ` · ${t('googleDoc')}` : ''}
      </span>
      <span className="mt-1 block text-xs text-slate-400">
        {file.type === 'FOLDER' ? t('folders') : formatSize(file.size)} · {updated}
      </span>
    </span>
  )
}

function FileActionProps(file: FileItem, state: ReturnType<typeof useExplorer>) {
  return {
    file,
    accounts: state.accounts,
    onDownload: state.downloadFile,
    onCopy: (item: FileItem) => state.openTransfer(item, 'COPY'),
    onMove: (item: FileItem) => state.openTransfer(item, 'MOVE'),
    onRename: state.confirmRename,
    onTrash: state.confirmTrash,
  }
}

function FileRow({ file, state, t }: {
  file: FileItem
  state: ReturnType<typeof useExplorer>
  t: (key: TranslationKey) => string
}) {
  return (
    <div className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b border-slate-100 px-4 py-3 last:border-0 hover:bg-slate-50/80 md:grid-cols-[minmax(0,1fr)_150px_100px_170px]">
      <button onClick={() => state.navigateFolder(file)} className="flex min-w-0 items-center gap-3 text-left">
        <span className={`grid size-10 shrink-0 place-items-center rounded-xl ${
          file.type === 'FOLDER' ? 'bg-violet-50 text-violet-600' : 'bg-slate-100 text-slate-500'
        }`}>
          <FileIcon file={file} className="size-4.5" />
        </span>
        <FileName file={file} t={t} />
      </button>
      <span className="hidden text-xs text-slate-500 md:block">
        {file.modifiedAt ? new Date(file.modifiedAt).toLocaleDateString() : '—'}
      </span>
      <span className="hidden text-xs text-slate-500 md:block">{formatSize(file.size)}</span>
      <span className="flex justify-end">
        <FileActions {...FileActionProps(file, state)} />
      </span>
    </div>
  )
}

function FileCard({ file, state, t }: {
  file: FileItem
  state: ReturnType<typeof useExplorer>
  t: (key: TranslationKey) => string
}) {
  const accountId = file.accountId ?? state.activeAccountId
  const thumbnailUrl = file.thumbnailUrl && accountId
    ? explorerApi.thumbnailUrl(accountId, file.id)
    : null

  return (
    <Card className="group overflow-hidden border-slate-200/80 bg-white shadow-sm shadow-slate-900/2 transition-all hover:-translate-y-0.5 hover:border-violet-200 hover:shadow-md">
      <button onClick={() => state.navigateFolder(file)} className="block w-full p-3 text-left">
        <span className="grid aspect-[1.65] place-items-center overflow-hidden rounded-xl bg-slate-50">
          {thumbnailUrl ? (
            <img src={thumbnailUrl} alt="" className="size-full object-cover" loading="lazy" />
          ) : (
            <FileIcon
              file={file}
              className={`size-9 ${file.type === 'FOLDER' ? 'text-violet-500' : 'text-slate-400'}`}
            />
          )}
        </span>
        <span className="mt-3 block">
          <FileName file={file} t={t} />
        </span>
      </button>
      <div className="flex justify-end px-2 pb-2">
        <FileActions {...FileActionProps(file, state)} />
      </div>
    </Card>
  )
}

function EmptyState({ hasAccount, t }: { hasAccount: boolean; t: (key: TranslationKey) => string }) {
  return (
    <div className="flex flex-col items-center px-6 py-16 text-center">
      <span className="grid size-14 place-items-center rounded-2xl bg-violet-50 text-violet-600">
        {hasAccount ? <Folder className="size-7" /> : <Cloud className="size-7" />}
      </span>
      <p className="mt-4 text-sm font-medium text-slate-800">
        {hasAccount ? t('emptyFolder') : t('connectToBrowse')}
      </p>
    </div>
  )
}

export function ExplorerPage({ accounts, accountsLoading, t }: ExplorerPageProps) {
  const state = useExplorer(accounts, t)
  const fileInput = useRef<HTMLInputElement>(null)

  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') state.refresh()
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-5 xl:flex-row xl:items-end">
        <div>
          <div className="flex items-center gap-2 text-sm font-medium text-violet-700">
            <span className="size-1.5 rounded-full bg-violet-500" />
            {t('workspaceLabel')}
          </div>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950 sm:text-[2rem]">
            {t('allFiles')}
          </h1>
          <p className="mt-2 text-sm text-slate-500">{t('browseAndOrganize')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input
            ref={fileInput}
            type="file"
            className="hidden"
            onChange={(event) => {
              void state.uploadFile(event.target.files?.[0])
              event.currentTarget.value = ''
            }}
          />
          <Button
            variant="outline"
            onClick={state.confirmCreateFolder}
            disabled={!state.activeAccountId || state.selectedCapabilities?.createFolder === false}
            className="border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
          >
            <Plus className="size-4" />
            {t('newFolder')}
          </Button>
          <Button
            variant="outline"
            onClick={() => fileInput.current?.click()}
            disabled={!state.activeAccountId || state.uploading || state.selectedCapabilities?.upload === false}
            className="border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
          >
            <Upload className="size-4" />
            {t('upload')}
          </Button>
          <Button
            onClick={state.refresh}
            disabled={!state.activeAccountId || state.loading}
            className="bg-violet-600 text-white hover:bg-violet-700"
          >
            <RefreshCw className={`size-4 ${state.loading ? 'animate-spin' : ''}`} />
            {t('refresh')}
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {accounts.map((account) => (
          <button
            key={account.id}
            onClick={() => state.selectAccount(account.id)}
            className={`flex items-center gap-2 rounded-xl border px-3 py-2 text-sm transition-colors ${
              state.activeAccountId === account.id
                ? 'border-violet-200 bg-violet-50 text-violet-800'
                : 'border-slate-200 bg-white text-slate-600 hover:border-violet-200 hover:bg-violet-50/40'
            }`}
          >
            <HardDrive className="size-4" />
            <span>{account.displayName}</span>
            <Badge variant="secondary" className="rounded-full bg-white px-2 py-0 text-[10px] text-slate-500">
              {providerLabel(account.provider)}
            </Badge>
            {state.activeAccountId === account.id && <Check className="size-3.5 text-violet-600" />}
          </button>
        ))}
        {!accountsLoading && accounts.length === 0 && (
          <p className="text-sm text-slate-500">{t('noProviderHint')}</p>
        )}
      </div>

      <Card className="border-slate-200/80 bg-white shadow-sm shadow-slate-900/2">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 xl:flex-row">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={state.searchQuery}
              onChange={(event) => state.setSearchQuery(event.target.value)}
              onKeyDown={handleSearchKeyDown}
              placeholder={t('search')}
              className="h-10 border-slate-200 bg-slate-50/70 pl-9 focus-visible:border-violet-400 focus-visible:ring-violet-400"
            />
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:flex">
            <Select value={state.fileType} onValueChange={state.setFileType}>
              <SelectTrigger className="h-10 w-full min-w-32 border-slate-200 bg-white text-slate-700 focus-visible:border-violet-400 focus-visible:ring-violet-400">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="border-slate-200 bg-white text-slate-700">
                <SelectItem value="all">{t('allTypes')}</SelectItem>
                <SelectItem value="folder">{t('folders')}</SelectItem>
                <SelectItem value="image">{t('images')}</SelectItem>
                <SelectItem value="pdf">{t('pdf')}</SelectItem>
                <SelectItem value="file">{t('files')}</SelectItem>
              </SelectContent>
            </Select>
            <Select value={state.sort} onValueChange={state.setSort}>
              <SelectTrigger className="h-10 w-full min-w-36 border-slate-200 bg-white text-slate-700 focus-visible:border-violet-400 focus-visible:ring-violet-400">
                <ArrowUpDown className="mr-1 size-4 text-slate-400" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="border-slate-200 bg-white text-slate-700">
                <SelectItem value="name"><ArrowDownAZ className="mr-2 size-4" />Name A–Z</SelectItem>
                <SelectItem value="name-desc">Name Z–A</SelectItem>
                <SelectItem value="size">Size: small first</SelectItem>
                <SelectItem value="size-desc">Size: large first</SelectItem>
                <SelectItem value="modified">Modified: oldest</SelectItem>
                <SelectItem value="modified-desc">Modified: newest</SelectItem>
              </SelectContent>
            </Select>
            <Select value={state.providerFilter} onValueChange={state.setProviderFilter}>
              <SelectTrigger className="col-span-2 h-10 w-full min-w-36 border-slate-200 bg-white text-slate-700 focus-visible:border-violet-400 focus-visible:ring-violet-400 sm:col-span-1">
                <SelectValue placeholder={t('allProviders')} />
              </SelectTrigger>
              <SelectContent className="border-slate-200 bg-white text-slate-700">
                <SelectItem value="all">{t('allProviders')}</SelectItem>
                <SelectItem value="GOOGLE">Google Drive</SelectItem>
                <SelectItem value="DROPBOX">Dropbox</SelectItem>
                <SelectItem value="ONEDRIVE">OneDrive</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {state.searchQuery.trim() && state.searchGroups.length > 0 && (
          <div className="grid gap-2 border-b border-slate-100 p-4 sm:grid-cols-2 xl:grid-cols-3">
            {state.searchGroups.map((group) => (
              <button
                key={group.accountId}
                onClick={() => state.selectAccount(group.accountId)}
                className="flex items-center justify-between rounded-xl border border-slate-100 px-3 py-2.5 text-left hover:border-violet-200 hover:bg-violet-50/40"
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-slate-800">{group.accountName}</span>
                  <span className="mt-0.5 block text-xs text-slate-500">{providerLabel(group.provider)}</span>
                </span>
                <span className={group.status === 'error' ? 'text-xs text-rose-600' : 'text-xs text-violet-700'}>
                  {group.status === 'error'
                    ? group.error || t('error')
                    : `${group.items.length} ${group.items.length === 1 ? t('result') : t('results')}`}
                </span>
              </button>
            ))}
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
          <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-1 overflow-x-auto text-sm">
            {state.breadcrumbs.map((crumb, index) => (
              <span key={`${crumb.id ?? 'root'}-${index}`} className="flex shrink-0 items-center">
                {index > 0 && <ChevronRight className="mx-1 size-4 text-slate-300" />}
                <button
                  onClick={() => state.navigateBreadcrumb(index)}
                  className={`max-w-40 truncate rounded px-1 py-0.5 ${
                    index === state.breadcrumbs.length - 1
                      ? 'font-medium text-slate-800'
                      : 'text-slate-500 hover:text-violet-700'
                  }`}
                >
                  {crumb.name}
                </button>
              </span>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <span className="hidden text-xs text-slate-400 sm:inline">
              {state.files.length} {t('files').toLowerCase()}
            </span>
            <div className="flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => state.setView('list')}
                aria-label={t('listView')}
                className={state.view === 'list' ? 'bg-white text-violet-700 shadow-sm' : 'text-slate-400'}
              >
                <List className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() => state.setView('grid')}
                aria-label={t('gridView')}
                className={state.view === 'grid' ? 'bg-white text-violet-700 shadow-sm' : 'text-slate-400'}
              >
                <Grid2X2 className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        {state.uploading && (
          <div className="border-b border-violet-100 bg-violet-50/50 px-4 py-3">
            <div className="mb-2 flex justify-between text-xs font-medium text-violet-800">
              <span>{t('uploading')}</span><span>{state.uploadProgress}%</span>
            </div>
            <Progress
              value={state.uploadProgress}
              className="h-1.5 bg-violet-100 **:data-[slot=progress-indicator]:bg-violet-600"
            />
          </div>
        )}

        {state.error && (
          <div className="p-4">
            <Alert variant="destructive" className="border-rose-200 bg-rose-50 text-rose-800">
              <AlertDescription>{state.error}</AlertDescription>
            </Alert>
          </div>
        )}

        {state.loading && state.files.length === 0 ? (
          <div className="flex items-center justify-center gap-2 px-6 py-16 text-sm text-slate-500">
            <RefreshCw className="size-4 animate-spin text-violet-600" />
            {t('loading')}...
          </div>
        ) : state.files.length === 0 ? (
          <EmptyState hasAccount={Boolean(state.activeAccountId)} t={t} />
        ) : state.view === 'list' ? (
          <div>
            <div className="hidden grid-cols-[minmax(0,1fr)_150px_100px_170px] border-b border-slate-100 bg-slate-50/70 px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 md:grid">
              <span>{t('name')}</span><span>{t('modified')}</span><span>{t('size')}</span><span />
            </div>
            {state.files.map((file) => (
              <FileRow key={`${file.accountId ?? state.activeAccountId}-${file.id}`} file={file} state={state} t={t} />
            ))}
          </div>
        ) : (
          <div className="grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
            {state.files.map((file) => (
              <FileCard key={`${file.accountId ?? state.activeAccountId}-${file.id}`} file={file} state={state} t={t} />
            ))}
          </div>
        )}

        {state.nextPageToken && !state.searchQuery.trim() && (
          <div className="border-t border-slate-100 p-4">
            <Button
              variant="outline"
              onClick={state.loadMore}
              disabled={state.loading}
              className="w-full border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
            >
              {state.loading ? <RefreshCw className="size-4 animate-spin" /> : null}
              {t('loadMore')}
            </Button>
          </div>
        )}
      </Card>

      <TransferDialog
        target={state.transferTarget}
        accounts={accounts}
        destinationAccount={state.transferDestinationAccount}
        destinationFolder={state.transferDestinationFolder}
        transferring={state.transferring}
        t={t}
        onOpenChange={(open) => {
          if (!open && !state.transferring) state.setTransferTarget(null)
        }}
        onDestinationAccountChange={state.setTransferDestinationAccount}
        onDestinationFolderChange={state.setTransferDestinationFolder}
        onTransfer={() => void state.transferFile()}
      />
    </div>
  )
}
