// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Account, FileItem } from '@cloudhub/contracts'
import { explorerApi } from '../api/explorer-api'
import { TransferDialog } from './transfer-dialog'

vi.mock('../api/explorer-api', () => ({ explorerApi: { listFiles: vi.fn() } }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const folder = (id: string, name: string): FileItem => ({
  id, name, type: 'FOLDER', mimeType: '', size: null, modifiedAt: null, shortcut: false,
  shortcutTargetId: null, nativeDoc: false, thumbnailUrl: null, previewable: false,
})

const accounts: Account[] = ['source', 'target', 'other'].map((id) => ({
  id, provider: 'GOOGLE', displayName: id, email: '', status: 'ACTIVE',
}))

describe('transfer destination', () => {
  it('loads more folders, browses down and resets to root when the account changes', async () => {
    vi.mocked(explorerApi.listFiles)
      .mockResolvedValueOnce({ items: [folder('top', 'First folder')], nextPageToken: 'page2' })
      .mockResolvedValueOnce({ items: [folder('sub', 'Second folder')], nextPageToken: '' })
      .mockResolvedValue({ items: [], nextPageToken: '' })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const transfer = vi.fn()
    const dialog = (account: string) => (
      <QueryClientProvider client={client}>
        <TransferDialog
          target={{ mode: 'MOVE', sourceAccountId: 'source', file: { ...folder('file', 'Test file'), type: 'FILE' } }}
          accounts={accounts}
          destinationAccount={account}
          transferring={false}
          partialMove={null}
          error=""
          t={(key) => key}
          onOpenChange={() => {}}
          onDestinationAccountChange={() => {}}
          onTransfer={transfer}
        />
      </QueryClientProvider>
    )

    const view = render(dialog('target'))
    fireEvent.click(await screen.findByRole('button', { name: 'moreFolders' }))
    expect(explorerApi.listFiles).toHaveBeenLastCalledWith('target', undefined, 'page2', 'folder', 'name')
    fireEvent.click(await screen.findByRole('button', { name: 'Second folder' }))
    expect(explorerApi.listFiles).toHaveBeenLastCalledWith('target', 'sub', '', 'folder', 'name')
    fireEvent.click(screen.getByRole('button', { name: 'move' }))
    expect(transfer).toHaveBeenLastCalledWith('sub')
    fireEvent.click(screen.getByRole('button', { name: 'parentFolder' }))
    expect(await screen.findByRole('button', { name: 'First folder' })).toBeDefined()

    view.rerender(dialog('other'))
    expect(await screen.findByText('noSubfolders')).toBeDefined()
    expect(explorerApi.listFiles).toHaveBeenLastCalledWith('other', undefined, '', 'folder', 'name')
    fireEvent.click(screen.getByRole('button', { name: 'move' }))
    expect(transfer).toHaveBeenLastCalledWith(undefined)
  })
})
