'use client'
import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { qk } from '@/lib/queryKeys'
import type { TrashItemType, TrashPage } from '@/types/trash'

/**
 * 워크스페이스 범위 휴지통 무한 페이지 쿼리 (M9.2 / Plan E T7).
 * docs/01 §6.1·§13, ADR #32 정합.
 *
 * - scopeType + scopeId 필수 — backend `GET /api/trash?scopeType&scopeId` 필수 파라미터와 1:1 대응.
 *   T6에서 확정된 `qk.trashList(scopeType, scopeId)` 시그니처를 첫 번째로 사용하는 호출자.
 * - queryKey: `qk.trashList(scopeType, scopeId)` (전체) 또는
 *   `[...qk.trashList(scopeType, scopeId), type]` (type 필터)
 *   → invalidateQueries({ queryKey: qk.trash() })가 prefix로 모두 매칭.
 * - enabled: scopeId가 빈 문자열이면 fetch 차단 (라우트 파라미터 미확정 방어).
 * - getNextPageParam: backend가 마지막 페이지에서 nextCursor 키를 생략(NON_NULL) 또는 null
 *   → undefined로 정규화하여 hasNextPage=false.
 * - initialPageParam: undefined — 첫 페이지는 cursor 없이 요청.
 */
export function useTrashList(opts: {
  scopeType: 'department' | 'team'
  scopeId: string
  type?: TrashItemType
}) {
  const { scopeType, scopeId, type } = opts
  const queryKey = type
    ? ([...qk.trashList(scopeType, scopeId), type] as const)
    : qk.trashList(scopeType, scopeId)

  return useInfiniteQuery<TrashPage, Error>({
    queryKey,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      api.getTrash({ scopeType, scopeId, cursor: pageParam as string | undefined, type }),
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: Boolean(scopeId),
  })
}
