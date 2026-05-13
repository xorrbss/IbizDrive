/**
 * User Home Dashboard 데이터 타입.
 *
 * v1.x `GET /api/me/shared-with-me` 응답 — backend {@code MySharedWithMeListResponse} 1:1.
 * favorites 측 타입은 `types/favorite.ts` reuse.
 */

export type MySharedWithMeItem = {
  permissionId: string
  resourceType: 'file' | 'folder'
  resourceId: string
  name: string
  /** backend preset 값 — 'read' | 'upload' | 'edit' | 'admin' (lowercase). */
  preset: string
  grantedAt: string
  grantedBy: { id: string; name: string }
}

export type MySharedWithMeListResponse = {
  items: MySharedWithMeItem[]
  /** v1.x 본 PR 에선 항상 null. cursor 페이지네이션은 follow-up. */
  nextCursor: string | null
}
