import { ArrowRightLeft, Copy, Download, Pencil, Trash2 } from 'lucide-react'
import { Button } from '@cloudhub/ui'
import type { Account, FileItem } from '@cloudhub/contracts'

interface FileActionsProps {
  file: FileItem
  accounts: Account[]
  onDownload: (file: FileItem) => void
  onCopy: (file: FileItem) => void
  onMove: (file: FileItem) => void
  onRename: (file: FileItem) => void
  onTrash: (file: FileItem) => void
}

export function FileActions({
  file,
  accounts,
  onDownload,
  onCopy,
  onMove,
  onRename,
  onTrash,
}: FileActionsProps) {
  const fileAccountId = file.accountId ?? accounts[0]?.id
  const capabilities = accounts.find((account) => account.id === fileAccountId)?.capabilities
  const isFile = file.type !== 'FOLDER' && !file.shortcut

  return (
    <div className="flex items-center justify-end gap-0.5">
      {isFile && (
        <>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => onDownload(file)}
            aria-label={`Download ${file.name}`}
            className="text-slate-400 hover:bg-violet-50 hover:text-violet-700"
          >
            <Download className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => onCopy(file)}
            disabled={capabilities?.download === false}
            aria-label={`Copy ${file.name}`}
            className="text-slate-400 hover:bg-violet-50 hover:text-violet-700"
          >
            <Copy className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => onMove(file)}
            disabled={capabilities?.download === false || capabilities?.trash === false}
            aria-label={`Move ${file.name}`}
            className="text-slate-400 hover:bg-amber-50 hover:text-amber-700"
          >
            <ArrowRightLeft className="size-4" />
          </Button>
        </>
      )}
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={() => onRename(file)}
        disabled={capabilities?.rename === false}
        aria-label={`Rename ${file.name}`}
        className="text-slate-400 hover:bg-violet-50 hover:text-violet-700"
      >
        <Pencil className="size-4" />
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        onClick={() => onTrash(file)}
        disabled={capabilities?.trash === false}
        aria-label={`Move ${file.name} to trash`}
        className="text-slate-400 hover:bg-rose-50 hover:text-rose-600"
      >
        <Trash2 className="size-4" />
      </Button>
    </div>
  )
}
