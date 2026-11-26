import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type {
  Account,
  Breadcrumb,
  FileItem,
  SearchGroup,
  TransferTarget,
} from '@cloudhub/contracts'
import type { TranslationKey } from '@cloudhub/i18n'
import { providerLabel } from '../../../shared/lib/format'
import { explorerApi } from '../api/explorer-api'

const selectedAccountStorageKey = 'cloudhub:selected-account'

export function useExplorer(accounts: Account[], t: (key: TranslationKey) => string) {
  const [selectedAccountId, setSelectedAccountId] = useState(
    () => window.localStorage.getItem(selectedAccountStorageKey),
  )
  const [folderId, setFolderId] = useState<string>()
  const [breadcrumbs, setBreadcrumbs] = useState<Breadcrumb[]>(() => [{ name: t('myDrive') }])
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
  const [providerFilter, setProviderFilter] = useState('all')
  const [searchGroups, setSearchGroups] = useState<SearchGroup[]>([])
  const [transferTarget, setTransferTarget] = useState<TransferTarget | null>(null)
  const [transferDestinationAccount, setTransferDestinationAccount] = useState('')
  const [transferDestinationFolder, setTransferDestinationFolder] = useState('')
  const [transferring, setTransferring] = useState(false)
  const requestId = useRef(0)

  const activeAccountId = useMemo(() => {
    if (selectedAccountId && accounts.some((account) => account.id === selectedAccountId)) {
      return selectedAccountId
    }
    return accounts[0]?.id ?? null
  }, [accounts, selectedAccountId])

  const selectedAccount = accounts.find((account) => account.id === activeAccountId) ?? null
  const selectedCapabilities = selectedAccount?.capabilities

  useEffect(() => {
    if (!activeAccountId) return
    window.localStorage.setItem(selectedAccountStorageKey, activeAccountId)
  }, [activeAccountId])

  const loadFiles = useCallback(async (
    accountId: string,
    parentId: string | undefined,
    pageToken = '',
    append = false,
  ) => {
    const currentRequestId = ++requestId.current
    setLoading(true)
    setError('')
    try {
      const value = await explorerApi.listFiles(accountId, parentId, pageToken, fileType, sort)
      if (currentRequestId !== requestId.current) return
      setFiles((current) => append ? [...current, ...value.items] : value.items)
      setNextPageToken(value.nextPageToken)
    } catch (reason) {
      if (currentRequestId === requestId.current) {
        setError(reason instanceof Error ? reason.message : 'Could not load files')
      }
    } finally {
      if (currentRequestId === requestId.current) setLoading(false)
    }
  }, [fileType, sort])

  const runSearch = useCallback(async () => {
    const query = searchQuery.trim()
    if (!query) {
      setSearchGroups([])
      setNextPageToken('')
      if (activeAccountId) await loadFiles(activeAccountId, folderId)
      return
    }

    const currentRequestId = ++requestId.current
    setLoading(true)
    setError('')
    try {
      const value = await explorerApi.search(query, fileType, sort, providerFilter)
      if (currentRequestId !== requestId.current) return
      setSearchGroups(value.groups)
      setFiles(value.groups.flatMap((group) =>
        group.items.map((item) => ({ ...item, accountId: group.accountId })),
      ))
      setNextPageToken('')
    } catch (reason) {
      if (currentRequestId === requestId.current) {
        setError(reason instanceof Error ? reason.message : 'Search failed')
      }
    } finally {
      if (currentRequestId === requestId.current) setLoading(false)
    }
  }, [activeAccountId, fileType, folderId, loadFiles, providerFilter, searchQuery, sort])

  useEffect(() => {
    if (!activeAccountId) return

    const timeout = window.setTimeout(() => {
      if (searchQuery.trim()) void runSearch()
      else void loadFiles(activeAccountId, folderId)
    }, searchQuery.trim() ? 250 : 0)
    return () => window.clearTimeout(timeout)
  }, [activeAccountId, fileType, folderId, loadFiles, providerFilter, runSearch, searchQuery, sort])

  function selectAccount(accountId: string) {
    const account = accounts.find((item) => item.id === accountId)
    setSelectedAccountId(accountId)
    setFolderId(undefined)
    setBreadcrumbs([{ name: account ? `${providerLabel(account.provider)} ${t('root')}` : t('myDrive') }])
    setSearchQuery('')
    setSearchGroups([])
  }

  function navigateFolder(file: FileItem) {
    if (!activeAccountId) return
    if (file.shortcut) {
      void resolveShortcut(file)
      return
    }
    if (file.type !== 'FOLDER') {
      previewFile(file)
      return
    }
    setBreadcrumbs((current) => [...current, { id: file.id, name: file.name }])
    setFolderId(file.id)
  }

  async function resolveShortcut(file: FileItem) {
    if (!activeAccountId || !file.shortcutTargetId) return
    setLoading(true)
    setError('')
    try {
      const target = await explorerApi.getFile(activeAccountId, file.shortcutTargetId)
      if (target.type === 'FOLDER') {
        setBreadcrumbs((current) => [...current, { id: target.id, name: target.name }])
        setFolderId(target.id)
      } else {
        previewFile(target)
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not resolve shortcut')
    } finally {
      setLoading(false)
    }
  }

  function navigateBreadcrumb(index: number) {
    const target = breadcrumbs[index]
    if (!target) return
    setBreadcrumbs((current) => current.slice(0, index + 1))
    setFolderId(target.id)
  }

  function downloadFile(file: FileItem) {
    const accountId = file.accountId ?? activeAccountId
    if (!accountId) return
    window.open(explorerApi.contentUrl(accountId, file.id), '_blank', 'noopener,noreferrer')
  }

  function previewFile(file: FileItem) {
    const accountId = file.accountId ?? activeAccountId
    if (!accountId) return
    const url = explorerApi.contentUrl(accountId, file.id, file.previewable)
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  async function createFolder(name: string) {
    if (!activeAccountId || !name.trim()) return
    setError('')
    try {
      await explorerApi.createFolder(activeAccountId, name.trim(), folderId)
      await loadFiles(activeAccountId, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not create folder')
    }
  }

  async function renameFile(file: FileItem, name: string) {
    const accountId = file.accountId ?? activeAccountId
    if (!accountId || !name.trim() || name.trim() === file.name) return
    setError('')
    try {
      await explorerApi.rename(accountId, file.id, name.trim())
      await loadFiles(activeAccountId ?? accountId, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not rename item')
    }
  }

  async function trashFile(file: FileItem) {
    const accountId = file.accountId ?? activeAccountId
    if (!accountId) return
    setError('')
    try {
      await explorerApi.trash(accountId, file.id)
      await loadFiles(activeAccountId ?? accountId, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not move item to trash')
    }
  }

  function openTransfer(file: FileItem, mode: 'COPY' | 'MOVE') {
    const sourceAccountId = file.accountId ?? activeAccountId
    if (!sourceAccountId) return
    const destination = accounts.find((account) => account.id !== sourceAccountId)
      ?? accounts.find((account) => account.id === sourceAccountId)
    setTransferTarget({ file, mode, sourceAccountId })
    setTransferDestinationAccount(destination?.id ?? '')
    setTransferDestinationFolder('')
  }

  async function transferFile() {
    if (!transferTarget || !transferDestinationAccount) return
    setTransferring(true)
    setError('')
    try {
      await explorerApi.transfer({
        sourceAccountId: transferTarget.sourceAccountId,
        sourceFileId: transferTarget.file.id,
        destinationAccountId: transferDestinationAccount,
        destinationFolderId: transferDestinationFolder.trim() || null,
        mode: transferTarget.mode,
      })
      setTransferTarget(null)
      setTransferDestinationFolder('')
      await loadFiles(activeAccountId ?? transferTarget.sourceAccountId, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not transfer file')
    } finally {
      setTransferring(false)
    }
  }

  async function uploadFile(file: File | undefined) {
    if (!file || !activeAccountId) return
    setUploading(true)
    setUploadProgress(0)
    setError('')
    try {
      await explorerApi.upload(activeAccountId, folderId, file, setUploadProgress)
      await loadFiles(activeAccountId, folderId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  function refresh() {
    if (activeAccountId) {
      if (searchQuery.trim()) void runSearch()
      else void loadFiles(activeAccountId, folderId)
    }
  }

  function loadMore() {
    if (activeAccountId && nextPageToken && !searchQuery.trim()) {
      void loadFiles(activeAccountId, folderId, nextPageToken, true)
    }
  }

  function confirmCreateFolder() {
    const name = window.prompt(t('folderName'))
    if (name?.trim()) void createFolder(name)
  }

  function confirmRename(file: FileItem) {
    const name = window.prompt(t('newName'), file.name)
    if (name?.trim() && name.trim() !== file.name) void renameFile(file, name)
  }

  function confirmTrash(file: FileItem) {
    const accountId = file.accountId ?? activeAccountId
    const account = accounts.find((item) => item.id === accountId)
    const provider = account ? providerLabel(account.provider) : 'cloud'
    if (window.confirm(`${t('moveToTrashConfirm')} "${file.name}" ${provider} ${t('toProviderTrash')}`)) {
      void trashFile(file)
    }
  }

  return {
    accounts,
    selectedAccount,
    activeAccountId,
    selectedCapabilities,
    folderId,
    breadcrumbs,
    files: activeAccountId ? files : [],
    nextPageToken,
    view,
    setView,
    error,
    loading,
    uploading,
    uploadProgress,
    searchQuery,
    setSearchQuery,
    fileType,
    setFileType,
    sort,
    setSort,
    providerFilter,
    setProviderFilter,
    searchGroups,
    transferTarget,
    setTransferTarget,
    transferDestinationAccount,
    setTransferDestinationAccount,
    transferDestinationFolder,
    setTransferDestinationFolder,
    transferring,
    selectAccount,
    navigateFolder,
    navigateBreadcrumb,
    downloadFile,
    previewFile,
    refresh,
    loadMore,
    uploadFile,
    confirmCreateFolder,
    confirmRename,
    confirmTrash,
    openTransfer,
    transferFile,
  }
}
