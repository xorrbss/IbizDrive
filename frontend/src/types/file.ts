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
  /**
   * 휴지통 이동 시각 (ISO 8601). NULL/undefined = active.
   * docs/01 §13: 백엔드 `files.deleted_at`. M9에서 frontend mock에 도입.
   */
  deletedAt?: string | null
  /**
   * 휴지통 이동 직전의 parentId 스냅샷. 복원 시 원위치 결정에 사용.
   * docs/01 §13.1: 백엔드 `files.original_parent`.
   */
  originalParentId?: string | null
}

export type SortKey = 'name' | 'updatedAt' | 'size'
