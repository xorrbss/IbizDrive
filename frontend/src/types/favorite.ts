/**
 * v1.x `GET /api/me/favorites` 응답 타입 — backend `FavoriteListResponse` 1:1.
 */

/** backend `ScopeRef` 1:1. workspace discriminator (`/d/:id` vs `/t/:id` URL prefix 결정용). */
export type ScopeRef = {
  type: 'department' | 'team'
  id: string
}

export type FavoriteItem = {
  resourceType: 'file' | 'folder'
  resourceId: string
  name: string
  /**
   * folder의 경우 부모 폴더 id (root면 null). file의 경우 file이 속한 폴더 id.
   * frontend가 URL 합성에 사용 — file 클릭 시 parentId로 이동 + ?file=resourceId,
   * folder 클릭 시 resourceId로 이동.
   */
  parentId: string | null
  /** workspace discriminator. backend @JsonInclude(NON_NULL)로 omit 가능 (방어적 optional). */
  scope?: ScopeRef
  /** favorites.created_at — server-side desc 정렬 보장. */
  starredAt: string
}

export type FavoritesListResponse = {
  items: FavoriteItem[]
}
