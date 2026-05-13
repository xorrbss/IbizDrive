/**
 * User Home Dashboard 데이터 타입.
 *
 * v1.x `GET /api/me/shared-with-me` 응답 — backend {@code MySharedWithMeListResponse} 1:1.
 * favorites 측 타입은 `types/favorite.ts` reuse.
 */

export type MySharedWithMeWorkspace = {
  /** 'department' | 'team' — backend ScopeType.dbValue() (lowercase). */
  kind: 'department' | 'team'
  id: string
}

export type MySharedWithMeItem = {
  permissionId: string
  resourceType: 'file' | 'folder'
  resourceId: string
  name: string
  /** backend preset 값 — 'read' | 'upload' | 'edit' | 'admin' (lowercase). */
  preset: string
  grantedAt: string
  grantedBy: { id: string; name: string }
  /** follow-up (2026-05-14) — row click 시 explorer 진입을 위한 workspace 컨텍스트. */
  workspace: MySharedWithMeWorkspace
  /**
   * follow-up (2026-05-14) — frontend 가 `buildWorkspacePath(workspace, navigationFolderId)` 호출.
   * file 의 경우 file.folder_id (parent folder) + `?file=resourceId` query 를 합성.
   * folder 의 경우 folder.id (자기 자신).
   */
  navigationFolderId: string
}

export type MySharedWithMeListResponse = {
  items: MySharedWithMeItem[]
  /** v1.x 본 PR 에선 항상 null. cursor 페이지네이션은 follow-up. */
  nextCursor: string | null
}
