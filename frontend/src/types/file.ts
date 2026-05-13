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
  /**
   * design-sweep-phase-2b: FileRow 배지(star/lock/share/items) 표시용. 모두 optional.
   * 백엔드 wiring(즐겨찾기/RBAC restricted/share count/folder itemsCount)은 v1.x.
   * 현재 호출부는 미전달 → undefined → 배지 비표시 (zip fidelity 우선).
   */
  starred?: boolean
  restricted?: boolean
  shareCount?: number
  itemsCount?: number | null
  /**
   * P_panel-A — RightPanel detail 응답 동봉 필드. {@code getFileDetail}만 채움 (list/mutation 응답은
   * 모두 undefined). FE는 RightPanel에서만 의미 있게 사용. docs/02 §7.6.
   */
  owner?: UserBrief | null
  sharedWith?: SubjectGrantBrief[]
  folderPath?: BreadcrumbCrumb[]
}

/** RightPanel owner 표시용 brief. 백엔드 `UserBriefDto`와 1:1. */
export type UserBrief = {
  id: string
  displayName: string
  email: string
}

/** RightPanel sharedWith 스택용 grant brief. 백엔드 `SubjectGrantBriefDto`와 1:1. */
export type SubjectGrantBrief = {
  subjectType: 'user' | 'department' | 'everyone' | string
  subjectId?: string | null
  subjectName?: string | null  // everyone → "전체", soft-delete subject → null
  preset: string  // 'read' | 'upload' | 'edit' | 'admin'
}

/** RightPanel folderPath 표시용 breadcrumb 원소. 백엔드 `BreadcrumbCrumbDto`와 1:1. */
export type BreadcrumbCrumb = {
  id: string
  name: string
  slug: string
}

export type SortKey = 'name' | 'updatedAt' | 'size'
