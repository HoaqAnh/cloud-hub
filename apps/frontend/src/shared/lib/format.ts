export function formatSize(size: number | null | undefined): string {
  if (size == null) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

export function providerLabel(provider: string): string {
  if (provider === 'DROPBOX') return 'Dropbox'
  if (provider === 'ONEDRIVE') return 'OneDrive'
  return 'Google Drive'
}
