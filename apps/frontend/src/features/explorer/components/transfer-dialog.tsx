import { useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ArrowRightLeft, Copy } from 'lucide-react'
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@cloudhub/ui'
import type { Account, TransferTarget } from '@cloudhub/contracts'
import type { Translate } from '@cloudhub/i18n'
import { explorerApi } from '../api/explorer-api'
import { providerLabel } from '../../../shared/lib/format'

interface TransferDialogProps {
  target: TransferTarget | null
  accounts: Account[]
  destinationAccount: string
  transferring: boolean
  partialMove: { fileName: string } | null
  error: string
  t: Translate
  onOpenChange: (open: boolean) => void
  onDestinationAccountChange: (accountId: string) => void
  onTransfer: (folderId?: string) => void
}

export function TransferDialog({
  target,
  accounts,
  destinationAccount,
  transferring,
  partialMove,
  error,
  t,
  onOpenChange,
  onDestinationAccountChange,
  onTransfer,
}: TransferDialogProps) {
  const destinations = accounts.filter((account) =>
    account.id !== target?.sourceAccountId && account.capabilities?.upload !== false,
  )

  return (
    <Dialog open={target !== null} onOpenChange={onOpenChange}>
      <DialogContent className="border-slate-200 bg-white text-slate-900 sm:max-w-md">
        <DialogHeader>
          <div className="mb-1 grid size-11 place-items-center rounded-2xl bg-violet-50 text-violet-700">
            {target?.mode === 'COPY'
              ? <Copy className="size-5" />
              : <ArrowRightLeft className="size-5" />}
          </div>
          <DialogTitle>{target?.mode === 'COPY' ? t('copy') : t('move')} {t('files').toLowerCase()}</DialogTitle>
          <DialogDescription className="truncate text-slate-500">
            {target?.file.name}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <label className="block space-y-2 text-sm font-medium">
            {t('destinationAccount')}
            <Select disabled={transferring || Boolean(partialMove)} value={destinationAccount} onValueChange={onDestinationAccountChange}>
              <SelectTrigger className="h-10 w-full border-slate-200 bg-white text-slate-800 focus-visible:border-violet-400 focus-visible:ring-violet-400">
                <SelectValue placeholder={t('destinationAccount')} />
              </SelectTrigger>
              <SelectContent className="border-slate-200 bg-white text-slate-800">
                {destinations.map((account) => (
                  <SelectItem
                    key={account.id}
                    value={account.id}
                    className="focus:bg-violet-50 focus:text-violet-800"
                  >
                    {account.displayName} · {providerLabel(account.provider)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </label>
        </div>

        {target && (
          <DestinationFolders
            key={destinationAccount}
            accountId={destinationAccount}
            mode={target.mode}
            pending={transferring}
            partialMove={partialMove}
            error={error}
            onCancel={() => onOpenChange(false)}
            onTransfer={onTransfer}
            t={t}
          />
        )}
      </DialogContent>
    </Dialog>
  )
}

function DestinationFolders({ accountId, mode, pending, partialMove, error, onCancel, onTransfer, t }: {
  accountId: string
  mode: 'COPY' | 'MOVE'
  pending: boolean
  partialMove: { fileName: string } | null
  error: string
  onCancel: () => void
  onTransfer: (folderId?: string) => void
  t: Translate
}) {
  const [path, setPath] = useState<{ id: string; name: string }[]>([])
  const folder = path.at(-1)
  const folderPath = [t('root'), ...path.map((item) => item.name)].join(' / ')
  const folders = useInfiniteQuery({
    queryKey: ['transfer-folders', accountId, folder?.id],
    enabled: Boolean(accountId) && !partialMove,
    initialPageParam: '',
    queryFn: ({ pageParam }) => explorerApi.listFiles(accountId, folder?.id, pageParam, 'folder', 'name'),
    getNextPageParam: (page) => page.nextPageToken || undefined,
  })
  const items = folders.data?.pages.flatMap((page) => page.items.filter((item) => item.type === 'FOLDER')) ?? []

  return (
    <>
      {accountId && !partialMove && (
        <div className="space-y-2 text-sm" aria-busy={folders.isFetching}>
          <p className="break-all">{t('destinationFolder')}: {folderPath}</p>
          {folder && (
            <Button type="button" size="sm" variant="outline" disabled={pending}
              onClick={() => setPath(path.slice(0, -1))}>{t('parentFolder')}</Button>
          )}
          <div className="max-h-48 overflow-y-auto">
            {items.map((item) => (
              <Button key={item.id} type="button" variant="ghost" className="w-full justify-start" disabled={pending}
                onClick={() => setPath([...path, { id: item.id, name: item.name }])}>{item.name}</Button>
            ))}
          </div>
          {folders.isPending && <p role="status">{t('loading')}</p>}
          {folders.isError && (
            <div role="alert">{t('error')}{' '}
              <Button type="button" variant="outline" size="sm"
                onClick={() => void (folders.isFetchNextPageError ? folders.fetchNextPage() : folders.refetch())}>
                {t('tryAgain')}
              </Button>
            </div>
          )}
          {!folders.isError && folders.hasNextPage && (
            <Button type="button" variant="outline" disabled={folders.isFetching || pending}
              onClick={() => void folders.fetchNextPage()}>{folders.isFetching ? t('loading') : t('moreFolders')}</Button>
          )}
          {!folders.isPending && !folders.isError && !items.length && !folders.hasNextPage && <p>{t('noSubfolders')}</p>}
        </div>
      )}
      {partialMove && (
        <div role="status" className="break-words text-sm">
          <p>{t('partialMove')}</p>
          <p>{folderPath} / {partialMove.fileName}</p>
        </div>
      )}
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <DialogFooter>
        <Button type="button" variant="outline" disabled={pending} onClick={onCancel}>{partialMove ? t('close') : t('cancel')}</Button>
        <Button type="button" disabled={pending || !accountId} onClick={() => onTransfer(folder?.id)}
          className="bg-violet-600 text-white hover:bg-violet-700">
          {pending ? t('loading') : partialMove ? t('retrySourceTrash') : mode === 'COPY' ? t('copy') : t('move')}
        </Button>
      </DialogFooter>
    </>
  )
}
