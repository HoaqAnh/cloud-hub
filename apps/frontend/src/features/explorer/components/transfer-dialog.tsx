import { ArrowRightLeft, Copy } from 'lucide-react'
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@cloudhub/ui'
import type { Account, TransferTarget } from '@cloudhub/contracts'
import type { TranslationKey } from '@cloudhub/i18n'
import { providerLabel } from '../../../shared/lib/format'

interface TransferDialogProps {
  target: TransferTarget | null
  accounts: Account[]
  destinationAccount: string
  destinationFolder: string
  transferring: boolean
  t: (key: TranslationKey) => string
  onOpenChange: (open: boolean) => void
  onDestinationAccountChange: (accountId: string) => void
  onDestinationFolderChange: (folderId: string) => void
  onTransfer: () => void
}

export function TransferDialog({
  target,
  accounts,
  destinationAccount,
  destinationFolder,
  transferring,
  t,
  onOpenChange,
  onDestinationAccountChange,
  onDestinationFolderChange,
  onTransfer,
}: TransferDialogProps) {
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
            <Select value={destinationAccount} onValueChange={onDestinationAccountChange}>
              <SelectTrigger className="h-10 w-full border-slate-200 bg-white text-slate-800 focus-visible:border-violet-400 focus-visible:ring-violet-400">
                <SelectValue placeholder={t('destinationAccount')} />
              </SelectTrigger>
              <SelectContent className="border-slate-200 bg-white text-slate-800">
                {accounts.map((account) => (
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
          <label className="block space-y-2 text-sm font-medium">
            <span>
              {t('destinationFolder')}
              <span className="ml-1 font-normal text-slate-400">({t('optional')})</span>
            </span>
            <Input
              value={destinationFolder}
              onChange={(event) => onDestinationFolderChange(event.target.value)}
              placeholder={t('rootFolderHint')}
              className="h-10 border-slate-200 bg-white focus-visible:border-violet-400 focus-visible:ring-violet-400"
            />
          </label>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={transferring}
            className="border-slate-200 bg-white text-slate-700 hover:bg-slate-50"
          >
            {t('cancel')}
          </Button>
          <Button
            onClick={onTransfer}
            disabled={transferring || !destinationAccount}
            className="bg-violet-600 text-white hover:bg-violet-700"
          >
            {transferring ? `${t('loading')}...` : target?.mode === 'COPY' ? t('copy') : t('move')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
