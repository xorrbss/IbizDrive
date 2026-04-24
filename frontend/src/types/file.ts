// src/types/file.ts

export type FileItem = {
  id: string
  name: string
  type: 'file' | 'folder'
  mimeType: string | null   // null for folders
  size: number | null        // bytes, null for folders
  updatedAt: string          // ISO 8601
  updatedBy: string          // user display name
  parentId: string
}

export type SortKey = 'name' | 'updatedAt' | 'size'
