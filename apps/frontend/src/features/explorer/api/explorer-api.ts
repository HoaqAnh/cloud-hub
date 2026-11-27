import { ApiError, apiRequest, getCsrfToken } from '@cloudhub/api-client'
import type { FileItem, SearchGroup } from '@cloudhub/contracts'

export interface FilePage {
  items: FileItem[]
  nextPageToken: string
}

export const explorerApi = {
  listFiles: (
    accountId: string,
    parentId: string | undefined,
    pageToken: string,
    type: string,
    sort: string,
  ) => {
    const query = new URLSearchParams({ type, sort })
    if (parentId) query.set('folderId', parentId)
    if (pageToken) query.set('pageToken', pageToken)
    return apiRequest<FilePage>(`/api/accounts/${accountId}/files?${query}`)
  },

  search: (query: string, type: string, sort: string, providers: string) => {
    const params = new URLSearchParams({ q: query, type, sort })
    if (providers !== 'all') params.set('providers', providers)
    return apiRequest<{ groups: SearchGroup[] }>(`/api/search?${params}`)
  },

  getFile: (accountId: string, fileId: string) =>
    apiRequest<FileItem>(`/api/accounts/${accountId}/files/${encodeURIComponent(fileId)}`),

  createFolder: (accountId: string, name: string, parentId?: string) => {
    const query = new URLSearchParams({ name })
    if (parentId) query.set('parentId', parentId)
    return apiRequest<FileItem>(`/api/accounts/${accountId}/folders?${query}`, {
      method: 'POST',
    })
  },

  rename: (accountId: string, fileId: string, name: string) =>
    apiRequest<FileItem>(
      `/api/accounts/${accountId}/files/${encodeURIComponent(fileId)}?name=${encodeURIComponent(name)}`,
      { method: 'PATCH' },
    ),

  trash: (accountId: string, fileId: string) =>
    apiRequest<{ trashed: boolean }>(
      `/api/accounts/${accountId}/files/${encodeURIComponent(fileId)}`,
      { method: 'DELETE' },
    ),

  transfer: (payload: {
    sourceAccountId: string
    sourceFileId: string
    destinationAccountId: string
    destinationFolderId: string | null
    mode: 'COPY' | 'MOVE'
  }) =>
    apiRequest<{ copiedFile: FileItem; moved: boolean; sourceFileId: string }>('/api/transfers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }),

  upload: async (
    accountId: string,
    folderId: string | undefined,
    file: File,
    onProgress: (progress: number) => void,
  ) => {
    const csrf = await getCsrfToken()
    return new Promise<void>((resolve, reject) => {
      const formData = new FormData()
      formData.append('file', file)
      const query = folderId ? `?parentId=${encodeURIComponent(folderId)}` : ''
      const request = new XMLHttpRequest()
      request.open('POST', `/api/accounts/${accountId}/files${query}`)
      request.withCredentials = true
      request.setRequestHeader(csrf.headerName, csrf.token)
      request.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }
      request.onload = () => {
        if (request.status >= 200 && request.status < 300) {
          resolve()
          return
        }

        let message = request.responseText || `Upload failed (${request.status})`
        let code: string | undefined
        if (request.responseText) {
          try {
            const body = JSON.parse(request.responseText) as { detail?: string; title?: string; code?: string }
            message = body.detail || body.title || message
            code = body.code
          } catch {
            // The provider may return plain text for upload failures.
          }
        }
        reject(new ApiError(code ? `${message} [${code}]` : message, request.status, code))
      }
      request.onerror = () => reject(new Error('Network error while uploading'))
      request.send(formData)
    })
  },

  contentUrl: (accountId: string, fileId: string, inline = false) =>
    `/api/accounts/${accountId}/files/${encodeURIComponent(fileId)}/content${inline ? '?disposition=inline' : ''}`,

  thumbnailUrl: (accountId: string, fileId: string) =>
    `/api/accounts/${accountId}/files/${encodeURIComponent(fileId)}/thumbnail`,
}
