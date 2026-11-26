export interface AuthUser {
  authenticated: boolean
  username?: string
}

export interface AccountCapabilities {
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

export interface Account {
  id: string
  provider: string
  displayName: string
  email: string
  status: string
  capabilities?: AccountCapabilities
}

export interface FileItem {
  id: string
  accountId?: string
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

export interface Breadcrumb {
  id?: string
  name: string
}

export interface StorageQuota {
  limit: number
  usage: number
  usageInDrive: number
  usageInTrash: number
}

export interface SearchGroup {
  accountId: string
  accountName: string
  provider: string
  status: 'ok' | 'error'
  items: FileItem[]
  nextPageToken?: string
  error?: string
}

export interface TransferTarget {
  file: FileItem
  mode: 'COPY' | 'MOVE'
  sourceAccountId: string
}
